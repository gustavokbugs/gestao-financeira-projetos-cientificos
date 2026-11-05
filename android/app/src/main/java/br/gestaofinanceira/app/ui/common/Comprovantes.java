package br.gestaofinanceira.app.ui.common;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * GP-05: prepara o arquivo escolhido para envio. Fotos são reduzidas (lado maior até 1600 px,
 * JPEG 80%) para ficarem bem abaixo do limite de 10 MB da API; PDFs vão como estão.
 */
public final class Comprovantes {

    public static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;
    private static final int LADO_MAXIMO = 1600;

    public static final class Preparado {
        public final byte[] conteudo;
        public final String mime;
        public final String nome;

        Preparado(byte[] conteudo, String mime, String nome) {
            this.conteudo = conteudo;
            this.mime = mime;
            this.nome = nome;
        }
    }

    public static final class ArquivoInvalido extends Exception {
        public ArquivoInvalido(String mensagem) {
            super(mensagem);
        }
    }

    private Comprovantes() {
    }

    /** Executar fora da thread principal. */
    public static Preparado preparar(ContentResolver cr, Uri uri, String nomePadrao) throws IOException, ArquivoInvalido {
        String mime = cr.getType(uri);
        String nome = nome(cr, uri, nomePadrao);
        if ("application/pdf".equals(mime)) {
            byte[] bytes = ler(cr, uri);
            if (bytes.length > TAMANHO_MAXIMO) throw new ArquivoInvalido("O PDF passa de 10 MB.");
            if (bytes.length == 0) throw new ArquivoInvalido("O arquivo está vazio.");
            return new Preparado(bytes, mime, nome);
        }
        if (mime != null && !mime.startsWith("image/")) {
            throw new ArquivoInvalido("Tipo de arquivo não aceito. Envie uma foto ou um PDF.");
        }
        byte[] jpeg = reduzir(cr, uri);
        String base = nome.contains(".") ? nome.substring(0, nome.lastIndexOf('.')) : nome;
        // O seletor de fotos do Android informa só o número da mídia ("20"): vira "imagem-20".
        if (base.matches("\\d+")) base = "imagem-" + base;
        return new Preparado(jpeg, "image/jpeg", base + ".jpg");
    }

    private static byte[] reduzir(ContentResolver cr, Uri uri) throws IOException, ArquivoInvalido {
        BitmapFactory.Options limites = new BitmapFactory.Options();
        limites.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, limites);
        }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) throw new ArquivoInvalido("Não foi possível ler a imagem.");
        int amostra = 1;
        while (Math.max(limites.outWidth, limites.outHeight) / (amostra * 2) >= LADO_MAXIMO) amostra *= 2;

        BitmapFactory.Options opcoes = new BitmapFactory.Options();
        opcoes.inSampleSize = amostra;
        Bitmap imagem;
        try (InputStream in = cr.openInputStream(uri)) {
            imagem = BitmapFactory.decodeStream(in, null, opcoes);
        }
        if (imagem == null) throw new ArquivoInvalido("Não foi possível ler a imagem.");

        float escala = Math.min(1f, (float) LADO_MAXIMO / Math.max(imagem.getWidth(), imagem.getHeight()));
        Matrix m = new Matrix();
        m.postScale(escala, escala);
        m.postRotate(rotacao(cr, uri));
        Bitmap final_ = Bitmap.createBitmap(imagem, 0, 0, imagem.getWidth(), imagem.getHeight(), m, true);

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        final_.compress(Bitmap.CompressFormat.JPEG, 80, saida);
        if (final_ != imagem) final_.recycle();
        imagem.recycle();
        return saida.toByteArray();
    }

    /** A câmera grava a orientação no EXIF; o JPEG reduzido perde o EXIF, então a rotação é aplicada. */
    private static int rotacao(ContentResolver cr, Uri uri) {
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return 0;
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static byte[] ler(ContentResolver cr, Uri uri) throws IOException {
        try (InputStream in = cr.openInputStream(uri); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("Arquivo indisponível");
            byte[] buffer = new byte[16 * 1024];
            int lidos;
            long totalLido = 0;
            while ((lidos = in.read(buffer)) != -1) {
                totalLido += lidos;
                if (totalLido > TAMANHO_MAXIMO + 1) break; // não precisa ler o resto: já passou do limite
                saida.write(buffer, 0, lidos);
            }
            return saida.toByteArray();
        }
    }

    private static String nome(ContentResolver cr, Uri uri, String padrao) {
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Exception ignorado) {
            // usa o nome padrão
        }
        return padrao;
    }
}
