'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { Pool, types } = require('pg');

// NUMERIC e BIGINT viram Number (os valores do sistema cabem com folga);
// DATE fica como texto 'AAAA-MM-DD', sem conversão de fuso horário.
types.setTypeParser(types.builtins.NUMERIC, (v) => Number(v));
types.setTypeParser(types.builtins.INT8, (v) => Number(v));
types.setTypeParser(types.builtins.DATE, (v) => v);

if (!process.env.DATABASE_URL) {
  console.error('Defina DATABASE_URL (ex.: postgres://usuario:senha@localhost:5432/gestao)');
  process.exit(1);
}

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: Number(process.env.PG_POOL_MAX || 5),
  idleTimeoutMillis: 30000,
});

const query = (texto, params) => pool.query(texto, params);

/** Executa fn(cliente) dentro de uma transação; desfaz tudo se algo falhar. */
async function transacao(fn) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');
    const resultado = await fn(cliente);
    await cliente.query('COMMIT');
    return resultado;
  } catch (erro) {
    await cliente.query('ROLLBACK').catch(() => {});
    throw erro;
  } finally {
    cliente.release();
  }
}

/** Aplica, em ordem, os arquivos de migrations/ que ainda não rodaram neste banco. */
async function migrar() {
  const pasta = path.join(__dirname, '..', 'migrations');
  const arquivos = fs.readdirSync(pasta).filter((a) => a.endsWith('.sql')).sort();
  const cliente = await pool.connect();
  try {
    await cliente.query('SELECT pg_advisory_lock(4815162342)'); // evita duas instâncias migrando ao mesmo tempo
    await cliente.query(`CREATE TABLE IF NOT EXISTS schema_migrations (
      versao TEXT PRIMARY KEY, aplicada_em TIMESTAMPTZ NOT NULL DEFAULT now())`);
    const { rows } = await cliente.query('SELECT versao FROM schema_migrations');
    const aplicadas = new Set(rows.map((r) => r.versao));
    for (const arquivo of arquivos) {
      if (aplicadas.has(arquivo)) continue;
      await cliente.query('BEGIN');
      try {
        await cliente.query(fs.readFileSync(path.join(pasta, arquivo), 'utf8'));
        await cliente.query('INSERT INTO schema_migrations (versao) VALUES ($1)', [arquivo]);
        await cliente.query('COMMIT');
        console.log(`Migração aplicada: ${arquivo}`);
      } catch (erro) {
        await cliente.query('ROLLBACK');
        throw erro;
      }
    }
  } finally {
    await cliente.query('SELECT pg_advisory_unlock(4815162342)').catch(() => {});
    cliente.release();
  }
}

module.exports = { pool, query, transacao, migrar };
