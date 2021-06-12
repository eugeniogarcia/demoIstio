package istio;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class UiController {
    //Obtiene el host y el puerto del servicio kubernetes - llamado webservice - a partir de las variables de entorno
    private static String WEBSERVICE_HOST = System.getenv("WEBSERVICE_SERVICE_HOST");
    private static String WEBSERVICE_PORT = System.getenv("WEBSERVICE_SERVICE_PORT");
    private static String webServiceUrl;
    
    @Value("${servicio.host:localhost}")
    private String host;

    @Value("${servicio.port:80}")
    private String port;

    @PostConstruct
    public void inicializa() {
        // uri del servicio
        webServiceUrl = "http://" + (WEBSERVICE_HOST!=null?WEBSERVICE_HOST:host)  + ":" + (WEBSERVICE_PORT!=null?WEBSERVICE_PORT:port);
	}


	@GetMapping("/")
    public ModelAndView page() throws MalformedURLException {
        ModelAndView mav = new ModelAndView("home");

        // fetch data from python service
        URL url = new URL(webServiceUrl);
        try {
            String response = IOUtils.toString(url.openStream(), "UTF-8");
            mav.addObject("respuesta", response);
        } catch (IOException e) {
            mav.addObject("respuesta", "Se ha recibido un error");
        }

        return mav;
    }
}
