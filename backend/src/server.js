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

// -------------------------------------------------------------- serialização

const autorDe = (usuario) => ({ id: usuario.id, nome: usuario.nome });

function projetoParaResposta(p) {
  return {
    id: p.id,
    codigo: p.codigo,
    titulo: p.titulo,
    descricao: p.descricao,
    vigenciaInicio: p.vigencia_inicio,
    vigenciaFim: p.vigencia_fim,
    fonteRecursos: p.fonte_recursos,
    responsaveis: p.responsaveis,
    orcamentoAprovado: p.orcamento_aprovado,
    papel: p.papel,
  };
}

const SELECT_LANCAMENTO = `
  SELECT l.*, cu.nome AS criado_por_nome, au.nome AS atualizado_por_nome,
         r.nome AS rubrica_nome, r.rubrica_pai_id AS rubrica_pai,
         (SELECT count(*) FROM comprovantes c WHERE c.lancamento_id = l.id) AS quantidade_comprovantes
    FROM lancamentos l
    JOIN usuarios cu ON cu.id = l.criado_por
    JOIN usuarios au ON au.id = l.atualizado_por
    LEFT JOIN rubricas r ON r.id = l.rubrica_id`;

function lancamentoParaResposta(l) {
  return {
    id: l.id,
    projetoId: l.projeto_id,
    tipo: l.tipo,
    data: l.data,
    descricao: l.descricao,
    rubricaId: l.rubrica_id,
    rubrica: l.rubrica_id ? { id: l.rubrica_id, nome: l.rubrica_nome, rubricaPaiId: l.rubrica_pai } : null,
    quantidade: l.quantidade,
    valorUnitario: l.valor_unitario,
    valorTotal: l.valor_total,
    situacao: l.situacao,
    criadoPor: { id: l.criado_por, nome: l.criado_por_nome },
    criadoEm: iso(l.criado_em),
    atualizadoPor: { id: l.atualizado_por, nome: l.atualizado_por_nome },
    atualizadoEm: iso(l.atualizado_em),
    quantidadeComprovantes: l.quantidade_comprovantes,
  };
}

function comprovanteParaResposta(c) {
  return {
    id: c.id,
    lancamentoId: c.lancamento_id,
    nomeArquivo: c.nome_arquivo,
    tipo: c.tipo,
    tamanho: c.tamanho,
    enviadoPor: { id: c.enviado_por, nome: c.enviado_por_nome },
    enviadoEm: iso(c.enviado_em),
    url: `${PREFIXO}/comprovantes/${c.id}`,
  };
}

function historicoParaResposta(h) {
  return {
    id: h.id,
    projetoId: h.projeto_id,
    entidade: h.entidade,
    entidadeId: h.entidade_id,
    acao: h.acao,
    usuario: { id: h.usuario_id, nome: h.usuario_nome },
    data: iso(h.data),
    alteracoes: h.alteracoes,
  };
}

async function buscarLancamento(id, c = { query }) {
  const { rows } = await c.query(`${SELECT_LANCAMENTO} WHERE l.id = $1`, [id]);
  return rows[0] || null;
}

// ------------------------------------------------------- cálculos financeiros

function calcularAlerta(orcamento, comprometido) {
  if (orcamento <= 0) {
    return comprometido > 0 ? { nivel: 'CRITICO', mensagem: 'Rubrica sem orçamento com despesas lançadas' } : null;
  }
  const percentual = (comprometido / orcamento) * 100;
  if (percentual > REGRAS_ALERTA.criticoPercentual) {
    return { nivel: 'CRITICO', mensagem: 'Saldo insuficiente: despesas acima do orçamento da rubrica' };
  }
  if (percentual >= REGRAS_ALERTA.atencaoPercentual) {
    return { nivel: 'ATENCAO', mensagem: `Rubrica próxima do limite (${percentual.toFixed(1).replace('.', ',')}% comprometido)` };
  }
  return null;
}

// Executado e previsto de cada rubrica; a rubrica principal soma também as subrubricas.
const SELECT_RESUMO_RUBRICAS = `
  SELECT r.id, r.nome, r.rubrica_pai_id, r.orcamento,
         COALESCE(SUM(l.valor_total) FILTER (WHERE l.situacao = 'REALIZADO'), 0) AS executado,
         COALESCE(SUM(l.valor_total) FILTER (WHERE l.situacao = 'PREVISTO'), 0) AS previsto
    FROM rubricas r
    LEFT JOIN lancamentos l
           ON l.tipo = 'DESPESA'
          AND (l.rubrica_id = r.id OR l.rubrica_id IN (SELECT s.id FROM rubricas s WHERE s.rubrica_pai_id = r.id))
   WHERE %FILTRO%
   GROUP BY r.id
   ORDER BY r.id`;

function resumoDeLinha(r) {
  const comprometido = arredondar(r.executado + r.previsto);
  return {
    id: r.id,
    nome: r.nome,
    rubricaPaiId: r.rubrica_pai_id,
    orcamento: r.orcamento,
    executado: r.executado,
    previsto: r.previsto,
    saldo: arredondar(r.orcamento - comprometido),
    percentualComprometido: r.orcamento > 0 ? arredondar((comprometido / r.orcamento) * 100) : null,
    alerta: calcularAlerta(r.orcamento, comprometido),
  };
}

async function resumoRubrica(rubricaId, c = { query }) {
  const { rows } = await c.query(SELECT_RESUMO_RUBRICAS.replace('%FILTRO%', 'r.id = $1'), [rubricaId]);
  return rows[0] ? resumoDeLinha(rows[0]) : null;
}

async function rubricasDoProjeto(projetoId) {
  const { rows } = await query(SELECT_RESUMO_RUBRICAS.replace('%FILTRO%', 'r.projeto_id = $1'), [projetoId]);
  const todas = rows.map(resumoDeLinha);
  return todas
    .filter((r) => r.rubricaPaiId === null)
    .map((pai) => ({ ...pai, subrubricas: todas.filter((r) => r.rubricaPaiId === pai.id) }));
}

async function dashboard(projeto) {
  const { rows } = await query(
    `SELECT tipo, COALESCE(SUM(valor_total), 0) AS total FROM lancamentos
      WHERE projeto_id = $1 AND situacao = 'REALIZADO' GROUP BY tipo`,
    [projeto.id],
  );
  const total = (tipo) => (rows.find((r) => r.tipo === tipo) || { total: 0 }).total;
  const receitaTotal = total('RECEITA');
  const despesaTotal = total('DESPESA');
  const rubricas = await rubricasDoProjeto(projeto.id);

  const alertas = [];
  for (const r of rubricas) {
    for (const item of [r, ...r.subrubricas]) {
      if (item.alerta) alertas.push({ rubricaId: item.id, rubrica: item.nome, ...item.alerta });
    }
  }

  return {
    projetoId: projeto.id,
    receitaTotal,
    despesaTotal,
    saldoDisponivel: arredondar(receitaTotal - despesaTotal),
    orcamentoPrevisto: projeto.orcamento_aprovado,
    orcamentoExecutado: despesaTotal,
    percentualExecutado: projeto.orcamento_aprovado > 0 ? arredondar((despesaTotal / projeto.orcamento_aprovado) * 100) : null,
    participacaoPorRubrica: rubricas.map((r) => ({
      rubricaId: r.id,
      rubrica: r.nome,
      valor: r.executado,
      percentual: despesaTotal > 0 ? arredondar((r.executado / despesaTotal) * 100) : 0,
    })),
    rubricas,
    alertas,
    regrasAlerta: REGRAS_ALERTA,
    atualizadoEm: new Date().toISOString(),
  };
}

// ------------------------------------------------------------------ histórico

async function registrarHistorico(c, projetoId, entidade, entidadeId, acao, usuario, alteracoes = []) {
  await c.query(
    `INSERT INTO historico (projeto_id, entidade, entidade_id, acao, usuario_id, alteracoes) VALUES ($1,$2,$3,$4,$5,$6)`,
    [projetoId, entidade, entidadeId, acao, usuario.id, JSON.stringify(alteracoes)],
  );
}

function diferencas(antes, depois, campos) {
  return campos
    .filter((c) => JSON.stringify(antes[c]) !== JSON.stringify(depois[c]))
    .map((c) => ({ campo: c, de: antes[c] ?? null, para: depois[c] ?? null }));
}

// ----------------------------------------------------------------- validações

function validarProjeto(d) {
  const erros = [];
  if (typeof d.titulo !== 'string' || !d.titulo.trim()) erros.push({ campo: 'titulo', mensagem: 'Informe o título' });
  if (typeof d.fonteRecursos !== 'string' || !d.fonteRecursos.trim()) erros.push({ campo: 'fonteRecursos', mensagem: 'Informe a fonte de recursos' });
  if (!dataValida(d.vigenciaInicio)) erros.push({ campo: 'vigenciaInicio', mensagem: 'Data inválida (use AAAA-MM-DD)' });
  if (!dataValida(d.vigenciaFim)) erros.push({ campo: 'vigenciaFim', mensagem: 'Data inválida (use AAAA-MM-DD)' });
  if (dataValida(d.vigenciaInicio) && dataValida(d.vigenciaFim) && d.vigenciaFim < d.vigenciaInicio) {
    erros.push({ campo: 'vigenciaFim', mensagem: 'O fim da vigência deve ser posterior ao início' });
  }
  if (!(numero(d.orcamentoAprovado) > 0)) erros.push({ campo: 'orcamentoAprovado', mensagem: 'Informe um orçamento maior que zero' });
  if (d.responsaveis !== undefined) {
    const ok = Array.isArray(d.responsaveis) && d.responsaveis.every((r) => r && typeof r.nome === 'string' && r.nome.trim());
    if (!ok) erros.push({ campo: 'responsaveis', mensagem: 'Cada responsável precisa de um nome' });
  }
  if (erros.length) throw new HttpError(422, 'Dados do projeto inválidos', erros);
}

async function validarRubrica(c, d, projeto, rubricaAtualId = null) {
  const erros = [];
  if (typeof d.nome !== 'string' || !d.nome.trim()) erros.push({ campo: 'nome', mensagem: 'Informe o nome da rubrica' });
  if (!(numero(d.orcamento) >= 0)) erros.push({ campo: 'orcamento', mensagem: 'Informe um orçamento igual ou maior que zero' });

  let pai = null;
  if (d.rubricaPaiId != null) {
    const { rows } = await c.query('SELECT * FROM rubricas WHERE id = $1 AND projeto_id = $2', [Number(d.rubricaPaiId) || 0, projeto.id]);
    pai = rows[0];
    if (!pai || pai.rubrica_pai_id !== null || pai.id === rubricaAtualId) {
      erros.push({ campo: 'rubricaPaiId', mensagem: 'Rubrica principal inválida (são permitidos dois níveis)' });
    }
  }
  if (erros.length) throw new HttpError(422, 'Dados da rubrica inválidos', erros);

  const { rows: irmas } = await c.query(
    `SELECT nome, orcamento FROM rubricas
      WHERE projeto_id = $1 AND rubrica_pai_id IS NOT DISTINCT FROM $2 AND id IS DISTINCT FROM $3`,
    [projeto.id, pai ? pai.id : null, rubricaAtualId],
  );
  if (irmas.some((r) => r.nome.toLowerCase() === d.nome.trim().toLowerCase())) {
    erros.push({ campo: 'nome', mensagem: 'Já existe uma rubrica com este nome' });
  }
  const soma = irmas.reduce((t, r) => t + r.orcamento, 0) + d.orcamento;
  const limite = pai ? pai.orcamento : projeto.orcamento_aprovado;
  if (soma > limite + 1e-9) {
    erros.push({
      campo: 'orcamento',
      mensagem: pai
        ? `A soma das subrubricas (${brl(soma)}) ultrapassa o orçamento de "${pai.nome}" (${brl(limite)})`
        : `A soma das rubricas (${brl(soma)}) ultrapassa o orçamento aprovado do projeto (${brl(limite)})`,
    });
  }
  if (erros.length) throw new HttpError(422, 'Dados da rubrica inválidos', erros);
  return pai;
}

async function validarLancamento(c, d, projeto) {
  const erros = [];
  if (!TIPOS_LANCAMENTO.includes(d.tipo)) erros.push({ campo: 'tipo', mensagem: 'Tipo deve ser RECEITA ou DESPESA' });
  if (!SITUACOES.includes(d.situacao)) erros.push({ campo: 'situacao', mensagem: 'Situação deve ser PREVISTO, REALIZADO ou CANCELADO' });
  if (typeof d.descricao !== 'string' || !d.descricao.trim()) erros.push({ campo: 'descricao', mensagem: 'Informe a descrição' });
  if (!dataValida(d.data)) {
    erros.push({ campo: 'data', mensagem: 'Data inválida (use AAAA-MM-DD)' });
  } else if (d.data < projeto.vigencia_inicio || d.data > projeto.vigencia_fim) {
    erros.push({ campo: 'data', mensagem: `A data deve estar na vigência do projeto (${projeto.vigencia_inicio} a ${projeto.vigencia_fim})` });
  }
  if (!(numero(d.quantidade) > 0)) erros.push({ campo: 'quantidade', mensagem: 'Quantidade deve ser maior que zero' });
  if (!(numero(d.valorUnitario) > 0)) erros.push({ campo: 'valorUnitario', mensagem: 'Valor unitário deve ser maior que zero' });

  if (d.rubricaId != null) {
    const { rows } = await c.query('SELECT 1 FROM rubricas WHERE id = $1 AND projeto_id = $2', [Number(d.rubricaId) || 0, projeto.id]);
    if (!rows[0]) erros.push({ campo: 'rubricaId', mensagem: 'Rubrica não pertence a este projeto' });
  } else if (d.tipo === 'DESPESA') {
    erros.push({ campo: 'rubricaId', mensagem: 'Informe a rubrica da despesa' });
  }
  if (erros.length) throw new HttpError(422, 'Dados do lançamento inválidos', erros);
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

rota('GET', '/auth/me', async ({ usuario }) => ({
  status: 200,
  corpo: { id: usuario.id, nome: usuario.nome, email: usuario.email, podeCriarProjetos: usuario.pode_criar_projetos },
}));

rota('GET', '/projetos', async ({ usuario }) => {
  const { rows } = await query(
    'SELECT p.*, m.papel FROM projetos p JOIN membros m ON m.projeto_id = p.id WHERE m.usuario_id = $1 ORDER BY p.id',
    [usuario.id],
  );
  return { status: 200, corpo: rows.map(projetoParaResposta) };
});

rota('POST', '/projetos', async ({ req, usuario }) => {
  if (!usuario.pode_criar_projetos) throw new HttpError(403, 'Seu usuário não pode cadastrar projetos');
  const d = await lerJson(req);
  validarProjeto(d);
  const projeto = await transacao(async (c) => {
    const { rows: [inserido] } = await c.query(
      `INSERT INTO projetos (codigo, titulo, descricao, vigencia_inicio, vigencia_fim, fonte_recursos, responsaveis, orcamento_aprovado)
       VALUES (COALESCE($1, 'TEMP-' || gen_random_uuid()), $2, $3, $4, $5, $6, $7, $8) RETURNING *`,
      [d.codigo ? String(d.codigo).trim() : null, d.titulo.trim(), d.descricao ? String(d.descricao) : '', d.vigenciaInicio, d.vigenciaFim,
        d.fonteRecursos.trim(), JSON.stringify(d.responsaveis || [{ nome: usuario.nome, funcao: 'Coordenador(a)' }]), d.orcamentoAprovado],
    );
    let p = inserido;
    if (!d.codigo) {
      // Código automático no formato PESQ-AAAA-NNN, usando o id gerado.
      ({ rows: [p] } = await c.query(
        `UPDATE projetos SET codigo = 'PESQ-' || to_char(now(), 'YYYY') || '-' || lpad(id::text, 3, '0') WHERE id = $1 RETURNING *`,
        [inserido.id],
      ));
    }
    await c.query('INSERT INTO membros (usuario_id, projeto_id, papel) VALUES ($1, $2, $3)', [usuario.id, p.id, 'coordenador']);
    await registrarHistorico(c, p.id, 'projeto', p.id, 'CRIACAO', usuario);
    return { ...p, papel: 'coordenador' };
  });
  return { status: 201, corpo: projetoParaResposta(projeto) };
});

rota('GET', '/projetos/:id', async ({ usuario, params }) => {
  const projeto = await exigirPermissao(usuario, idDaRota(params.id), 'ler');
  return { status: 200, corpo: projetoParaResposta(projeto) };
});

rota('PUT', '/projetos/:id', async ({ req, usuario, params }) => {
  const projetoId = idDaRota(params.id);
  const d = await lerJson(req);
  const atualizado = await transacao(async (c) => {
    const projeto = await exigirPermissao(usuario, projetoId, 'gerenciar', c);
    const antes = projetoParaResposta(projeto);
    const campos = ['titulo', 'descricao', 'vigenciaInicio', 'vigenciaFim', 'fonteRecursos', 'responsaveis', 'orcamentoAprovado'];
    const novo = { ...antes };
    for (const campo of campos) if (d[campo] !== undefined) novo[campo] = d[campo];
    validarProjeto(novo);
    const { rows: [soma] } = await c.query(
      'SELECT COALESCE(SUM(orcamento), 0) AS total FROM rubricas WHERE projeto_id = $1 AND rubrica_pai_id IS NULL',
      [projetoId],
    );
    if (novo.orcamentoAprovado + 1e-9 < soma.total) {
      throw new HttpError(422, 'Dados do projeto inválidos', [
        { campo: 'orcamentoAprovado', mensagem: `O orçamento não pode ser menor que a soma das rubricas (${brl(soma.total)})` },
      ]);
    }
    const { rows: [p] } = await c.query(
      `UPDATE projetos SET titulo = $2, descricao = $3, vigencia_inicio = $4, vigencia_fim = $5, fonte_recursos = $6,
              responsaveis = $7, orcamento_aprovado = $8 WHERE id = $1 RETURNING *`,
      [projetoId, novo.titulo.trim(), String(novo.descricao ?? ''), novo.vigenciaInicio, novo.vigenciaFim, novo.fonteRecursos.trim(),
        JSON.stringify(novo.responsaveis), novo.orcamentoAprovado],
    );
    const alteracoes = diferencas(antes, novo, campos);
    if (alteracoes.length) await registrarHistorico(c, projetoId, 'projeto', projetoId, 'ALTERACAO', usuario, alteracoes);
    return { ...p, papel: projeto.papel };
  });
  return { status: 200, corpo: projetoParaResposta(atualizado) };
});

rota('GET', '/projetos/:id/dashboard', async ({ usuario, params }) => {
  const projeto = await exigirPermissao(usuario, idDaRota(params.id), 'ler');
  return { status: 200, corpo: await dashboard(projeto) };
});

rota('GET', '/projetos/:id/rubricas', async ({ usuario, params }) => {
  const projeto = await exigirPermissao(usuario, idDaRota(params.id), 'ler');
  return { status: 200, corpo: await rubricasDoProjeto(projeto.id) };
});

rota('POST', '/projetos/:id/rubricas', async ({ req, usuario, params }) => {
  const projetoId = idDaRota(params.id);
  const d = await lerJson(req);
  const resumo = await transacao(async (c) => {
    const projeto = await exigirPermissao(usuario, projetoId, 'gerenciar', c);
    const pai = await validarRubrica(c, d, projeto);
    const { rows: [r] } = await c.query(
      'INSERT INTO rubricas (projeto_id, nome, rubrica_pai_id, orcamento) VALUES ($1, $2, $3, $4) RETURNING id',
      [projetoId, d.nome.trim(), pai ? pai.id : null, d.orcamento],
    );
    await registrarHistorico(c, projetoId, 'rubrica', r.id, 'CRIACAO', usuario);
    return resumoRubrica(r.id, c);
  });
  return { status: 201, corpo: resumo };
});

rota('PUT', '/rubricas/:id', async ({ req, usuario, params }) => {
  const rubricaId = idDaRota(params.id);
  const d = await lerJson(req);
  const resumo = await transacao(async (c) => {
    const { rows: [rubrica] } = await c.query('SELECT * FROM rubricas WHERE id = $1', [rubricaId]);
    if (!rubrica) throw new HttpError(404, 'Rubrica não encontrada');
    const projeto = await exigirPermissao(usuario, rubrica.projeto_id, 'gerenciar', c);
    const antes = { nome: rubrica.nome, orcamento: rubrica.orcamento };
    const novo = { nome: d.nome ?? rubrica.nome, orcamento: d.orcamento ?? rubrica.orcamento, rubricaPaiId: rubrica.rubrica_pai_id };
    await validarRubrica(c, novo, projeto, rubricaId);
    const { rows: [filhas] } = await c.query('SELECT COALESCE(SUM(orcamento), 0) AS total FROM rubricas WHERE rubrica_pai_id = $1', [rubricaId]);
    if (novo.orcamento + 1e-9 < filhas.total) {
      throw new HttpError(422, 'Dados da rubrica inválidos', [
        { campo: 'orcamento', mensagem: `O orçamento não pode ser menor que a soma das subrubricas (${brl(filhas.total)})` },
      ]);
    }
    novo.nome = novo.nome.trim();
    await c.query('UPDATE rubricas SET nome = $2, orcamento = $3 WHERE id = $1', [rubricaId, novo.nome, novo.orcamento]);
    const alteracoes = diferencas(antes, novo, ['nome', 'orcamento']);
    if (alteracoes.length) await registrarHistorico(c, rubrica.projeto_id, 'rubrica', rubricaId, 'ALTERACAO', usuario, alteracoes);
    return resumoRubrica(rubricaId, c);
  });
  return { status: 200, corpo: resumo };
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
