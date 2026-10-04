package cl.restaurante.api.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;

@Service
public class InventoryAlertService {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    public InventoryAlertService(JdbcTemplate jdbc,NotificationService notifications,PlatformTransactionManager manager) { this.jdbc=jdbc;this.notifications=notifications;this.tx=new TransactionTemplate(manager); }
    @Scheduled(fixedDelayString="${marigex.stock.delay:300000}",initialDelayString="${marigex.stock.initial-delay:90000}")
    public void scan() {
        for(long id:jdbc.query("SELECT ID_INSUMO FROM APP_INSUMO ORDER BY ID_INSUMO",(rs,n)->rs.getLong(1))) check(id);
    }
    public void check(long id) {
        tx.executeWithoutResult(status->{
            var rows=jdbc.query("SELECT NOMBRE,STOCK_ACTUAL,STOCK_MINIMO,STOCK_MAXIMO,ALERTA_ESTADO,UNIDAD FROM APP_INSUMO WHERE ID_INSUMO=? FOR UPDATE",
                (rs,n)->new Stock(rs.getString(1),rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getString(5),rs.getString(6)),id);
            if(rows.isEmpty())return;
            var s=rows.getFirst();
            String state=s.current().compareTo(s.min())<=0?"BAJO":s.max()!=null&&s.current().compareTo(s.max())>0?"ALTO":"NORMAL";
            if(state.equals(s.previous()))return;
            jdbc.update("UPDATE APP_INSUMO SET ALERTA_ESTADO=? WHERE ID_INSUMO=?",state,id);
            if(!state.equals("NORMAL")) notifications.administrators("Inventario",(state.equals("BAJO")?"Stock bajo: ":"Sobrestock: ")+s.name()+". Stock actual: "+s.current()+" "+s.unit()+". "+(state.equals("BAJO")?"Mínimo: "+s.min():"Máximo: "+s.max()));
        });
    }
    private record Stock(String name,BigDecimal current,BigDecimal min,BigDecimal max,String previous,String unit) {}
}
