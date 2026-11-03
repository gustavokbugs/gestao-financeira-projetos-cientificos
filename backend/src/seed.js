'use strict';

// Dados de demonstração. Só são inseridos com o banco vazio (sem usuários).
//
// Projeto 1: planilha de orçamento do enunciado (docs/referencias/exemplo-planilha-orcamento.png).
//   Os lançamentos exercitam os alertas do GP-10: Passagens estourada; Diárias e Material permanente perto do limite.
// Projeto 2: sem o perfil gestor, para testar o controle de acesso por projeto.
// Projeto 3: valores do painel financeiro do enunciado (docs/referencias/exemplo-painel-financeiro.png),
//   para conferir o CA-01: receita 559.453,02 - despesas 96.078,83 = saldo 463.374,19.
//
// Uso: npm run seed   (ou SEED_DEMO=true ao iniciar a API)

const crypto = require('node:crypto');

function hashSenha(senha) {
  const salt = crypto.randomBytes(16).toString('hex');
  return { salt, hash: crypto.scryptSync(senha, salt, 32).toString('hex') };
}

const USUARIOS = [
  { id: 1, nome: 'Coordenação Exemplo', email: 'coordenador@exemplo.com', podeCriarProjetos: true },
  { id: 2, nome: 'Gestão Exemplo', email: 'gestor@exemplo.com', podeCriarProjetos: false },
  { id: 3, nome: 'Consulta Exemplo', email: 'consulta@exemplo.com', podeCriarProjetos: false },
];

const PROJETOS = [
  {
    id: 1, codigo: 'PESQ-2026-001', titulo: 'Seminário de Pesquisa em Computação',
    descricao: 'Projeto de exemplo baseado na planilha de orçamento do enunciado.',
    vigenciaInicio: '2026-03-01', vigenciaFim: '2027-02-28', fonteRecursos: 'Edital de fomento (exemplo)',
    responsaveis: [{ nome: 'Coordenação Exemplo', funcao: 'Coordenador(a)' }, { nome: 'Gestão Exemplo', funcao: 'Gestor(a) financeiro(a)' }],
    orcamentoAprovado: 30000,
  },
  {
    id: 2, codigo: 'PESQ-2026-002', titulo: 'Laboratório de Dados Abertos',
    descricao: 'Segundo projeto, sem o perfil gestor, para testar o controle de acesso por projeto.',
    vigenciaInicio: '2026-01-01', vigenciaFim: '2026-12-31', fonteRecursos: 'Recursos próprios da instituição (exemplo)',
    responsaveis: [{ nome: 'Coordenação Exemplo', funcao: 'Coordenador(a)' }],
    orcamentoAprovado: 12000,
  },
  {
    id: 3, codigo: 'PESQ-2026-003', titulo: 'Programa de Pesquisa Aplicada (painel de referência)',
    descricao: 'Reproduz os valores do painel financeiro do enunciado (CA-01).',
    vigenciaInicio: '2026-01-01', vigenciaFim: '2027-12-31', fonteRecursos: 'Convênio (exemplo)',
    responsaveis: [{ nome: 'Coordenação Exemplo', funcao: 'Coordenador(a)' }],
    orcamentoAprovado: 600000,
  },
];

const MEMBROS = [
  [1, 1, 'coordenador'], [2, 1, 'gestor'], [3, 1, 'consulta'],
  [1, 2, 'coordenador'], [3, 2, 'consulta'],
  [1, 3, 'coordenador'], [3, 3, 'consulta'],
];

// [id, projeto, nome, pai, orçamento]
const RUBRICAS = [
  [1, 1, 'Diárias', null, 1600],
  [2, 1, 'Passagens', null, 2000],
  [3, 1, 'Material de consumo', null, 1900],
  [4, 1, 'Serviços de terceiros', null, 1000],
  [5, 1, 'Material permanente', null, 23500],
  [6, 1, 'Passagens rodoviárias', 2, 200],
  [7, 1, 'Passagens aéreas', 2, 1800],
  [8, 2, 'Material de consumo', null, 4000],
  [9, 2, 'Serviços de terceiros', null, 8000],
  [10, 3, 'Custo com pessoal', null, 50000],
  [11, 3, 'Despesas operacionais', null, 150000],
  [12, 3, 'Estágio', null, 12000],
];

// [projeto, tipo, data, descrição, rubrica, quantidade, valor unitário, situação, autor]
const LANCAMENTOS = [
  [1, 'RECEITA', '2026-03-10', 'Repasse da 1ª parcela do fomento', null, 1, 20000, 'REALIZADO', 1],
  [1, 'RECEITA', '2026-09-10', 'Repasse da 2ª parcela do fomento', null, 1, 10000, 'PREVISTO', 1],
  [1, 'DESPESA', '2026-04-15', 'Diárias nacionais - evento em Brasília', 1, 4, 320, 'REALIZADO', 2],
  [1, 'DESPESA', '2026-04-12', 'Santa Cruz do Sul - Porto Alegre (rodoviário ida/volta)', 6, 1, 200, 'REALIZADO', 2],
  [1, 'DESPESA', '2026-04-12', 'Porto Alegre - Brasília (aéreo ida/volta)', 7, 1, 2150, 'REALIZADO', 2],
  [1, 'DESPESA', '2026-05-03', 'Mouse Magic Apple', 3, 1, 1000, 'REALIZADO', 2],
  [1, 'DESPESA', '2026-05-03', 'Teclado sem fio Logitech compatível Apple', 3, 1, 250, 'REALIZADO', 2],
  [1, 'DESPESA', '2026-06-20', 'Adaptador USB-C/USB', 3, 2, 100, 'PREVISTO', 2],
  [1, 'DESPESA', '2026-05-20', 'Material de divulgação do seminário', 4, 1, 500, 'REALIZADO', 1],
  [1, 'DESPESA', '2026-06-02', 'Notebook Apple MacBook Pro 13" M2', 5, 1, 14000, 'REALIZADO', 1],
  [1, 'DESPESA', '2026-07-01', 'Notebook Dell Intel Core i7 16GB RAM 14"', 5, 1, 8500, 'PREVISTO', 1],
  [1, 'DESPESA', '2026-06-02', 'Monitor 24 polegadas', 5, 1, 1000, 'CANCELADO', 1],
  [2, 'RECEITA', '2026-01-15', 'Aporte institucional', null, 1, 12000, 'REALIZADO', 1],
  [2, 'DESPESA', '2026-02-01', 'Hospedagem de servidor (12 meses)', 9, 12, 300, 'REALIZADO', 1],
  [3, 'RECEITA', '2026-02-01', 'Repasse do convênio', null, 1, 559453.02, 'REALIZADO', 1],
  [3, 'DESPESA', '2026-03-05', 'Custo com pessoal', 10, 1, 989.83, 'REALIZADO', 1],
  [3, 'DESPESA', '2026-03-20', 'Despesa operacional', 11, 1, 89089.0, 'REALIZADO', 1],
  [3, 'DESPESA', '2026-04-10', 'Bolsa de estágio', 12, 1, 6000, 'REALIZADO', 1],
];

/** Insere os dados de demonstração se o banco ainda não tiver usuários. Retorna true se inseriu. */
async function popularSeVazio(transacao, senha = process.env.SEED_SENHA || 'senha123') {
  return transacao(async (c) => {
    const { rows } = await c.query('SELECT count(*) AS n FROM usuarios');
    if (rows[0].n > 0) return false;

    for (const u of USUARIOS) {
      const { salt, hash } = hashSenha(senha);
      await c.query(
        'INSERT INTO usuarios (id, nome, email, senha_salt, senha_hash, pode_criar_projetos) VALUES ($1,$2,$3,$4,$5,$6)',
        [u.id, u.nome, u.email, salt, hash, u.podeCriarProjetos],
      );
    }
    for (const p of PROJETOS) {
      await c.query(
        `INSERT INTO projetos (id, codigo, titulo, descricao, vigencia_inicio, vigencia_fim, fonte_recursos, responsaveis, orcamento_aprovado)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
        [p.id, p.codigo, p.titulo, p.descricao, p.vigenciaInicio, p.vigenciaFim, p.fonteRecursos, JSON.stringify(p.responsaveis), p.orcamentoAprovado],
      );
    }
    for (const [usuario, projeto, papel] of MEMBROS) {
      await c.query('INSERT INTO membros (usuario_id, projeto_id, papel) VALUES ($1,$2,$3)', [usuario, projeto, papel]);
    }
    for (const [id, projeto, nome, pai, orcamento] of RUBRICAS) {
      await c.query('INSERT INTO rubricas (id, projeto_id, nome, rubrica_pai_id, orcamento) VALUES ($1,$2,$3,$4,$5)', [id, projeto, nome, pai, orcamento]);
    }
    for (const [projeto, tipo, data, descricao, rubrica, qtd, unitario, situacao, autor] of LANCAMENTOS) {
      const momento = `${data}T12:00:00.000Z`;
      const { rows: [l] } = await c.query(
        `INSERT INTO lancamentos (projeto_id, tipo, data, descricao, rubrica_id, quantidade, valor_unitario, valor_total,
                                  situacao, criado_por, criado_em, atualizado_por, atualizado_em)
         VALUES ($1,$2,$3,$4,$5,$6,$7, ROUND(ROUND($6::numeric, 3) * ROUND($7::numeric, 2), 2), $8,$9,$10,$9,$10) RETURNING id`,
        [projeto, tipo, data, descricao, rubrica, qtd, unitario, situacao, autor, momento],
      );
      await c.query(
        `INSERT INTO historico (projeto_id, entidade, entidade_id, acao, usuario_id, data) VALUES ($1,'lancamento',$2,'CRIACAO',$3,$4)`,
        [projeto, l.id, autor, momento],
      );
    }
    // Os IDs foram fixados acima; ajusta as sequências para os próximos cadastros.
    for (const tabela of ['usuarios', 'projetos', 'rubricas']) {
      await c.query(`SELECT setval(pg_get_serial_sequence('${tabela}', 'id'), (SELECT max(id) FROM ${tabela}))`);
    }
    return true;
  });
}

module.exports = { popularSeVazio };

if (require.main === module) {
  const db = require('./db');
  db.migrar()
    .then(() => popularSeVazio(db.transacao))
    .then((inseriu) => console.log(inseriu ? 'Dados de demonstração inseridos.' : 'O banco já tem dados; nada foi inserido.'))
    .catch((erro) => {
      console.error(erro);
      process.exitCode = 1;
    })
    .finally(() => db.pool.end());
}
