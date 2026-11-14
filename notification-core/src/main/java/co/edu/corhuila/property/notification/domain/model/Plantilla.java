package co.edu.corhuila.property.notification.domain.model;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A row of message_template (06-data/models.md): subject only for EMAIL. */
public record Plantilla(String codigo, Canal canal, String asunto, String cuerpo) {

    private static final Pattern MARCADOR = Pattern.compile("\\{\\{([a-zA-Z]+)\\}\\}");

    /** Fills every {{name}}. A placeholder without a value is a broken template, never sent half-filled. */
    public Mensaje render(Map<String, String> valores) {
        return new Mensaje(asunto == null ? null : fill(asunto, valores), fill(cuerpo, valores));
    }

    private String fill(String texto, Map<String, String> valores) {
        Matcher m = MARCADOR.matcher(texto);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String valor = valores.get(m.group(1));
            if (valor == null) {
                throw new IllegalStateException("the template " + codigo + " needs " + m.group(1));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(valor));
        }
        return m.appendTail(out).toString();
    }

    public record Mensaje(String asunto, String cuerpo) { }
}
