'use strict';

// API REST do microsserviço de gestão financeira (Opção 1), com PostgreSQL.
// Contrato: docs/api-endpoints.md. Configuração: ver .env.example.

const http = require('node:http');
const crypto = require('node:crypto');
const { query, transacao, migrar, pool } = require('./db');
const { popularSeVazio } = require('./seed');
const { gerarPdf } = require('./pdf');

const PRODUCAO = process.env.NODE_ENV === 'production';
const PORT = Number(process.env.PORT || 3000);
const JWT_SECRET = process.env.JWT_SECRET || (PRODUCAO ? null : crypto.randomBytes(32).toString('hex'));
const JWT_VALIDADE_S = Number(process.env.JWT_VALIDADE_HORAS || 8) * 3600;
const ATRASO_MS = Number(process.env.MOCK_DELAY_MS || 0);
const TAXA_FALHA = Number(process.env.MOCK_FAIL_RATE || 0);
const TAMANHO_MAX_JSON = 1024 * 1024;
const TAMANHO_MAX_COMPROVANTE = 10 * 1024 * 1024;
const TIPOS_COMPROVANTE = new Set(['image/jpeg', 'image/png', 'application/pdf']);
const PREFIXO = '/api/v1';

if (!JWT_SECRET || JWT_SECRET.length < 32) {
  console.error('Em produção, defina JWT_SECRET com pelo menos 32 caracteres.');
  process.exit(1);
}

// Regras de alerta do GP-10: configuráveis no servidor; o app só exibe o resultado.
const REGRAS_ALERTA = {
  atencaoPercentual: Number(process.env.ALERTA_ATENCAO_PERCENTUAL || 80),
  criticoPercentual: Number(process.env.ALERTA_CRITICO_PERCENTUAL || 100),
};

const PERMISSOES = {
  coordenador: ['ler', 'lancar', 'gerenciar'],
  gestor: ['ler', 'lancar'],
  consulta: ['ler'],
};
const TIPOS_LANCAMENTO = ['RECEITA', 'DESPESA'];
const SITUACOES = ['PREVISTO', 'REALIZADO', 'CANCELADO'];

// ---------------------------------------------------------------- utilitários

class HttpError extends Error {
  constructor(status, mensagem, erros) {
    super(mensagem);
    this.status = status;
    this.erros = erros;
  }
}

const arredondar = (valor) => Math.round(valor * 100) / 100;
const dataValida = (s) => typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(s));
const numero = (v) => (typeof v === 'number' && Number.isFinite(v) ? v : NaN);
const brl = (v) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v);
const iso = (d) => (d instanceof Date ? d.toISOString() : d);

function idDaRota(valor) {
  const id = Number(valor);
  if (!Number.isInteger(id) || id <= 0 || id > 2147483647) throw new HttpError(404, 'Recurso não encontrado');
  return id;
}

function lerCorpo(req, limite) {
  return new Promise((resolve, reject) => {
    const partes = [];
    let tamanho = 0;
    req.on('data', (parte) => {
      tamanho += parte.length;
      if (tamanho > limite) {
        reject(new HttpError(413, 'Conteúdo excede o tamanho máximo permitido'));
        req.destroy();
        return;
      }
      partes.push(parte);
    });
    req.on('end', () => resolve(Buffer.concat(partes)));
    req.on('error', reject);
  });
}

async function lerJson(req) {
  const corpo = await lerCorpo(req, TAMANHO_MAX_JSON);
  if (corpo.length === 0) return {};
  try {
    const dados = JSON.parse(corpo.toString('utf8'));
    if (dados === null || typeof dados !== 'object' || Array.isArray(dados)) throw new Error();
    return dados;
  } catch {
    throw new HttpError(400, 'Corpo da requisição não é um JSON válido');
  }
}

function responder(res, status, corpo) {
  const json = JSON.stringify(corpo);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(json) });
  res.end(json);
}

function responderArquivo(res, conteudo, tipo, nomeArquivo) {
  res.writeHead(200, {
    'Content-Type': tipo,
    'Content-Length': conteudo.length,
    'Content-Disposition': `attachment; filename="${nomeArquivo.replace(/["\r\n]/g, '')}"`,
  });
  res.end(conteudo);
}

function ipDe(req) {
  // Atrás do proxy da nuvem, o último IP de X-Forwarded-For é o que o proxy viu (o primeiro pode ser forjado).
  const encaminhado = req.headers['x-forwarded-for'];
  return (typeof encaminhado === 'string' && encaminhado.split(',').pop().trim()) || req.socket.remoteAddress || '';
}

// ------------------------------------------------------------- autenticação

function assinarToken(usuario) {
  const agora = Math.floor(Date.now() / 1000);
  const exp = agora + JWT_VALIDADE_S;
  const cabecalho = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const carga = Buffer.from(JSON.stringify({ sub: String(usuario.id), nome: usuario.nome, iat: agora, exp })).toString('base64url');
  const assinatura = crypto.createHmac('sha256', JWT_SECRET).update(`${cabecalho}.${carga}`).digest('base64url');
  return { token: `${cabecalho}.${carga}.${assinatura}`, expiraEm: new Date(exp * 1000).toISOString() };
}

function verificarToken(token) {
  const partes = token.split('.');
  if (partes.length !== 3) return null;
  const [cabecalho, carga, assinatura] = partes;
  const esperada = crypto.createHmac('sha256', JWT_SECRET).update(`${cabecalho}.${carga}`).digest();
  const recebida = Buffer.from(assinatura, 'base64url');
  if (recebida.length !== esperada.length || !crypto.timingSafeEqual(recebida, esperada)) return null;
  try {
    const { alg } = JSON.parse(Buffer.from(cabecalho, 'base64url').toString('utf8'));
    const dados = JSON.parse(Buffer.from(carga, 'base64url').toString('utf8'));
    if (alg !== 'HS256' || typeof dados.exp !== 'number' || dados.exp <= Date.now() / 1000) return null;
    return dados;
  } catch {
    return null;
  }
}

function senhaConfere(linha, senha) {
  const calculado = crypto.scryptSync(String(senha), linha.senha_salt, 32);
  return crypto.timingSafeEqual(calculado, Buffer.from(linha.senha_hash, 'hex'));
}

// Limite simples de tentativas de login: 10 falhas em 15 minutos por IP + e-mail.
const tentativasLogin = new Map();
const JANELA_LOGIN_MS = 15 * 60 * 1000;

function verificarLimiteLogin(chave) {
  const registro = tentativasLogin.get(chave);
  if (registro && registro.ate > Date.now() && registro.falhas >= 10) {
    throw new HttpError(429, 'Muitas tentativas de login. Aguarde alguns minutos e tente novamente.');
  }
}

function registrarFalhaLogin(chave) {
  const agora = Date.now();
  const registro = tentativasLogin.get(chave);
  if (!registro || registro.ate <= agora) tentativasLogin.set(chave, { falhas: 1, ate: agora + JANELA_LOGIN_MS });
  else registro.falhas += 1;
  if (tentativasLogin.size > 10000) {
    for (const [k, v] of tentativasLogin) if (v.ate <= agora) tentativasLogin.delete(k);
  }
}

async function autenticar(req) {
  const [esquema, token] = (req.headers.authorization || '').split(' ');
  const dados = esquema === 'Bearer' && token ? verificarToken(token) : null;
  if (dados) {
    const { rows } = await query('SELECT id, nome, email, pode_criar_projetos FROM usuarios WHERE id = $1', [Number(dados.sub)]);
    if (rows[0]) return rows[0];
  }
  throw new HttpError(401, 'Sessão inválida ou expirada. Faça login novamente.');
}

// Projetos dos quais o usuário não participa respondem 404, para não revelar que existem.
async function exigirPermissao(usuario, projetoId, acao, c = { query }) {
  const { rows } = await c.query(
    'SELECT p.*, m.papel FROM projetos p JOIN membros m ON m.projeto_id = p.id AND m.usuario_id = $2 WHERE p.id = $1',
    [projetoId, usuario.id],
  );
  const projeto = rows[0];
  if (!projeto) throw new HttpError(404, 'Projeto não encontrado');
  if (!PERMISSOES[projeto.papel].includes(acao)) {
    throw new HttpError(403, `O perfil "${projeto.papel}" não permite esta operação neste projeto`);
  }
  return projeto;
}

// -------------------------------------------------------------------- rotas

const rotas = [];
const rota = (metodo, padrao, handler) => {
  const regex = new RegExp(`^${PREFIXO}${padrao.replace(/:(\w+)/g, '(?<$1>[^/]+)')}$`);
  rotas.push({ metodo, regex, handler });
};

rota('GET', '/status', async () => {
  await query('SELECT 1');
  return { status: 200, corpo: { status: 'ok', banco: 'ok', horario: new Date().toISOString() } };
});

rota('POST', '/auth/login', async ({ req }) => {
  const { email, senha } = await lerJson(req);
  if (!email || !senha) {
    throw new HttpError(422, 'Informe e-mail e senha', [
      ...(!email ? [{ campo: 'email', mensagem: 'Informe o e-mail' }] : []),
      ...(!senha ? [{ campo: 'senha', mensagem: 'Informe a senha' }] : []),
    ]);
  }
  const emailNormalizado = String(email).trim().toLowerCase();
  const chave = `${ipDe(req)}|${emailNormalizado}`;
  verificarLimiteLogin(chave);
  const { rows } = await query('SELECT * FROM usuarios WHERE email = $1', [emailNormalizado]);
  const usuario = rows[0];
  if (!usuario || !senhaConfere(usuario, senha)) {
    registrarFalhaLogin(chave);
    throw new HttpError(401, 'E-mail ou senha incorretos');
  }
  tentativasLogin.delete(chave);
  const { token, expiraEm } = assinarToken(usuario);
  return {
    status: 200,
    corpo: { token, tipo: 'Bearer', expiraEm, usuario: { id: usuario.id, nome: usuario.nome, email: usuario.email, podeCriarProjetos: usuario.pode_criar_projetos } },
  };
});

// ------------------------------------------------------------------ servidor

const ROTAS_PUBLICAS = new Set([`${PREFIXO}/status`, `${PREFIXO}/auth/login`]);

async function tratar(req, res) {
  const url = new URL(req.url, 'http://localhost');
  if (ATRASO_MS > 0) await new Promise((r) => setTimeout(r, ATRASO_MS));
  if (TAXA_FALHA > 0 && Math.random() < TAXA_FALHA && url.pathname !== `${PREFIXO}/status`) {
    throw new HttpError(503, 'Serviço temporariamente indisponível (falha simulada)');
  }

  let caminhoConhecido = false;
  for (const r of rotas) {
    const encontrado = r.regex.exec(url.pathname);
    if (!encontrado) continue;
    caminhoConhecido = true;
    if (r.metodo !== req.method) continue;
    const usuario = ROTAS_PUBLICAS.has(url.pathname) ? null : await autenticar(req);
    const resultado = await r.handler({ req, usuario, params: encontrado.groups || {}, query: url.searchParams });
    if (resultado.arquivo) {
      responderArquivo(res, resultado.arquivo.conteudo, resultado.arquivo.tipo, resultado.arquivo.nome);
    } else {
      responder(res, resultado.status, resultado.corpo);
    }
    return;
  }
  throw caminhoConhecido ? new HttpError(405, 'Método não permitido') : new HttpError(404, 'Recurso não encontrado');
}

// Erros do PostgreSQL que indicam dado inválido, e não falha do servidor.
const ERROS_PG = {
  23505: [422, 'Já existe um registro com estes dados'],
  23503: [422, 'Registro relacionado não encontrado'],
  23514: [422, 'Dados fora das regras permitidas'],
  '22P02': [400, 'Valor em formato inválido'],
  22003: [422, 'Valor numérico fora do limite permitido'],
  22008: [422, 'Data fora do intervalo permitido'],
};

const servidor = http.createServer((req, res) => {
  const inicio = Date.now();
  res.on('finish', () => console.log(`${new Date().toISOString()} ${req.method} ${req.url} ${res.statusCode} ${Date.now() - inicio}ms`));
  tratar(req, res).catch((erro) => {
    if (res.headersSent) return res.destroy();
    if (erro instanceof HttpError) {
      return responder(res, erro.status, { mensagem: erro.message, ...(erro.erros ? { erros: erro.erros } : {}) });
    }
    if (ERROS_PG[erro.code]) {
      const [status, mensagem] = ERROS_PG[erro.code];
      return responder(res, status, { mensagem });
    }
    if (['ECONNREFUSED', 'ENOTFOUND', 'ETIMEDOUT', '57P01', '53300'].includes(erro.code)) {
      console.error('Banco de dados indisponível:', erro.message);
      return responder(res, 503, { mensagem: 'Serviço temporariamente indisponível. Tente novamente em instantes.' });
    }
    console.error(erro);
    responder(res, 500, { mensagem: 'Erro interno do servidor' });
  });
});

async function iniciar() {
  await migrar();
  if (process.env.SEED_DEMO === 'true' && (await popularSeVazio(transacao))) {
    console.log('Dados de demonstração inseridos.');
  }
  servidor.listen(PORT, () => {
    console.log(`API em http://localhost:${PORT}${PREFIXO}`);
    if (ATRASO_MS) console.log(`Atraso simulado: ${ATRASO_MS}ms`);
    if (TAXA_FALHA) console.log(`Taxa de falha simulada: ${TAXA_FALHA * 100}%`);
  });
}

function encerrar() {
  servidor.close(() => pool.end().finally(() => process.exit(0)));
  setTimeout(() => process.exit(0), 10000).unref();
}
process.on('SIGTERM', encerrar);
process.on('SIGINT', encerrar);

iniciar().catch((erro) => {
  console.error('Falha ao iniciar a API:', erro);
  process.exit(1);
});
