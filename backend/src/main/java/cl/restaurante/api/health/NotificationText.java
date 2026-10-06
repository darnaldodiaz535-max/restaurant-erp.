package cl.restaurante.api.health;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;

final class NotificationText {
    private NotificationText() {}

    static String readClob(ResultSet rs, String column) throws SQLException {
        Clob clob = rs.getClob(column);
        if (clob == null) {
            return null;
        }

        try (Reader reader = clob.getCharacterStream()) {
            StringWriter writer = new StringWriter();
            reader.transferTo(writer);
            return writer.toString();
        } catch (IOException ex) {
            throw new SQLException("No se pudo leer el detalle de la notificación.", ex);
        }
    }
}
