package logired.backend.legado;

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import logired.backend.ruteo.dto.RutaRequest;
import logired.backend.ruteo.dto.RutaResponse;
import logired.backend.ruteo.service.RuteoService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/ws")
public class LegacySoapController {

    private final RuteoService ruteoService;

    public LegacySoapController(RuteoService ruteoService) {
        this.ruteoService = ruteoService;
    }

    @GetMapping(value = "/legacy-ruteo.wsdl", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<byte[]> wsdl() throws Exception {
        byte[] body = StreamUtils.copyToByteArray(
                new ClassPathResource("wsdl/legacy-ruteo.wsdl").getInputStream());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @PostMapping(value = "/legacy-ruteo", consumes = {
            "text/xml",
            "application/soap+xml"
    }, produces = "text/xml")
    public ResponseEntity<byte[]> recibirSolicitud(HttpServletRequest request) throws Exception {
        byte[] xml = request.getInputStream().readAllBytes();
        if (xml.length > 65536) {
            return crearRespuestaSoap(construirFault("El mensaje SOAP excede el tamaño permitido."));
        }

        String xmlText = new String(xml, StandardCharsets.UTF_8);
        if (containsXxeMarkers(xmlText)) {
            return crearRespuestaSoap(construirFault("Mensaje SOAP rechazado: entidades o DOCTYPE no están permitidos."));
        }

        try {
            Document document = secureXmlDocument(xml);
            Element requestElement = buscarElemento(document, "CalcularRutaRequest");
            if (requestElement == null) {
                return crearRespuestaSoap(construirFault("No se encontró CalcularRutaRequest en el SOAP Body."));
            }

            List<String> direcciones = leerTexto(requestElement, "direccion");
            if (direcciones.isEmpty()) {
                return crearRespuestaSoap(construirFault("Debe incluir al menos una dirección."));
            }

            String criterio = leerPrimerTexto(requestElement, "criterio");
            RutaResponse response = ruteoService.calcularRutaConEstrategia(
                    new RutaRequest(direcciones, criterio));
            return crearRespuestaSoap(construirRespuesta(response));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return crearRespuestaSoap(construirFault(exception.getMessage()));
        } catch (Exception exception) {
            return crearRespuestaSoap(construirFault("Solicitud SOAP inválida."));
        }
    }

    private Document secureXmlDocument(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml));
    }

    private boolean containsXxeMarkers(String xml) {
        String lower = xml.toLowerCase();
        return lower.contains("<!doctype") || lower.contains("<!entity")
                || lower.contains("&xxe;");
    }

    private Element buscarElemento(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private List<String> leerTexto(Element parent, String localName) {
        List<String> valores = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            String texto = nodes.item(i).getTextContent();
            if (texto != null && !texto.trim().isEmpty()) {
                valores.add(texto.trim());
            }
        }
        return valores;
    }

    private String leerPrimerTexto(Element parent, String localName) {
        List<String> valores = leerTexto(parent, localName);
        return valores.isEmpty() ? null : valores.get(0);
    }

    private String construirRespuesta(RutaResponse response) {
        StringBuilder paradas = new StringBuilder();
        if (response.getParadasOrdenadas() != null) {
            for (String parada : response.getParadasOrdenadas()) {
                paradas.append("<direccion>").append(escapeXml(parada)).append("</direccion>");
            }
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
                " xmlns:lg=\"http://logired/backend/legacy\">" +
                "<soap:Body><lg:CalcularRutaResponse>" +
                "<lg:paradasOrdenadas>" + paradas + "</lg:paradasOrdenadas>" +
                "<lg:distanciaTotalKm>" + response.getDistanciaTotalKm() + "</lg:distanciaTotalKm>" +
                "<lg:tiempoEstimadoMinutos>" + response.getTiempoEstimadoMinutos() + "</lg:tiempoEstimadoMinutos>" +
                "<lg:estrategiaAplicada>" + escapeXml(response.getEstrategiaAplicada()) + "</lg:estrategiaAplicada>" +
                "<lg:mensaje>" + escapeXml(response.getMensaje()) + "</lg:mensaje>" +
                "</lg:CalcularRutaResponse></soap:Body></soap:Envelope>";
    }

    private String construirFault(String mensaje) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                "<soap:Body><soap:Fault><faultcode>soap:Client</faultcode>" +
                "<faultstring>" + escapeXml(mensaje) + "</faultstring></soap:Fault>" +
                "</soap:Body></soap:Envelope>";
    }

    private ResponseEntity<byte[]> crearRespuestaSoap(String xml) throws Exception {
        SOAPMessage message = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL)
                .createMessage(null, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/xml"));
        return ResponseEntity.ok().headers(headers).body(output.toByteArray());
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
