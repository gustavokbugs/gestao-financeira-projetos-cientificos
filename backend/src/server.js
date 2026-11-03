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
