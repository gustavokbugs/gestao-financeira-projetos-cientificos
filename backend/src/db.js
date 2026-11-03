'use strict';

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

module.exports = { pool, query, transacao };
