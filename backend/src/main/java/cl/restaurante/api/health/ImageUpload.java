package cl.restaurante.api.health;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ImageUpload {
    private ImageUpload() {}
    static String validate(byte[] b,String claimed) {
        String actual=null;
        if(b.length>=12 && b.length<=4*1024*1024) {
            if((b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255) actual="image/jpeg";
            else if((b[0]&255)==137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10) actual="image/png";
            else if(b[0]==82 && b[1]==73 && b[2]==70 && b[3]==70 && b[8]==87 && b[9]==69 && b[10]==66 && b[11]==80) actual="image/webp";
        }
        if(actual==null || !actual.equals(claimed)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecciona una imagen JPG, PNG o WebP de hasta 4 MB.");
        return actual;
    }
    static String safeName(String name) {
        String value=name==null?"horario":name.replaceAll("[^A-Za-z0-9._-]","_");
        return value.substring(Math.max(0,value.length()-255));
    }
}
