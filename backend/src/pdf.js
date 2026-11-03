'use strict';

// Gerador mínimo de PDF (A4, só texto) para o relatório financeiro (GP-08).
// Não usa bibliotecas externas: escreve os objetos PDF diretamente.

const LARGURA = 595;
const ALTURA = 842;
const MARGEM = 50;

const FONTES = { normal: 'F1', negrito: 'F2', mono: 'F3' };

// O PDF usa WinAnsiEncoding; caracteres fora de Latin-1 são trocados.
function paraLatin1(texto) {
  return String(texto)
    .replace(/[–—]/g, '-')
    .replace(/[‘’]/g, "'")
    .replace(/[“”]/g, '"')
    .replace(/[^\x00-\xFF]/g, '?');
}

function escapar(texto) {
  return paraLatin1(texto).replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
}

/**
 * @param {Array<{texto: string, tamanho?: number, fonte?: 'normal'|'negrito'|'mono'}>} linhas
 * @returns {Buffer}
 */
function gerarPdf(linhas) {
  const paginas = [];
  let atual = [];
  let y = ALTURA - MARGEM;

  for (const linha of linhas) {
    const tamanho = linha.tamanho || 10;
    const passo = tamanho * 1.45;
    if (y - passo < MARGEM) {
      paginas.push(atual);
      atual = [];
      y = ALTURA - MARGEM;
    }
    y -= passo;
    if (linha.texto) {
      const fonte = FONTES[linha.fonte || 'normal'];
      atual.push(`BT /${fonte} ${tamanho} Tf ${MARGEM} ${y.toFixed(2)} Td (${escapar(linha.texto)}) Tj ET`);
    }
  }
  paginas.push(atual);

  // Objetos: 1 catálogo, 2 páginas, 3-5 fontes, depois (página, conteúdo) por página.
  const objetos = [];
  const idsPaginas = paginas.map((_, i) => 6 + i * 2);

  objetos[1] = '<< /Type /Catalog /Pages 2 0 R >>';
  objetos[2] = `<< /Type /Pages /Kids [${idsPaginas.map((id) => `${id} 0 R`).join(' ')}] /Count ${paginas.length} >>`;
  objetos[3] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>';
  objetos[4] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>';
  objetos[5] = '<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>';

  paginas.forEach((comandos, i) => {
    const idPagina = idsPaginas[i];
    const conteudo = comandos.join('\n');
    objetos[idPagina] =
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${LARGURA} ${ALTURA}] ` +
      `/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> /Contents ${idPagina + 1} 0 R >>`;
    objetos[idPagina + 1] = `<< /Length ${Buffer.byteLength(conteudo, 'latin1')} >>\nstream\n${conteudo}\nendstream`;
  });

  let saida = '%PDF-1.4\n';
  const offsets = [];
  for (let id = 1; id < objetos.length; id++) {
    offsets[id] = Buffer.byteLength(saida, 'latin1');
    saida += `${id} 0 obj\n${objetos[id]}\nendobj\n`;
  }
  const inicioXref = Buffer.byteLength(saida, 'latin1');
  saida += `xref\n0 ${objetos.length}\n0000000000 65535 f \n`;
  for (let id = 1; id < objetos.length; id++) {
    saida += `${String(offsets[id]).padStart(10, '0')} 00000 n \n`;
  }
  saida += `trailer\n<< /Size ${objetos.length} /Root 1 0 R >>\nstartxref\n${inicioXref}\n%%EOF\n`;

  return Buffer.from(saida, 'latin1');
}

module.exports = { gerarPdf, paraLatin1 };
