/* ========================================================================
 * PlantUML : a free UML diagram generator
 * ========================================================================
 *
 * Project Info:  https://plantuml.com
 *
 * This file is part of PlantUML.
 *
 * PlantUML is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PlantUML distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 */
package net.sourceforge.plantuml.servlet;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import net.sourceforge.plantuml.BlockUml;
import net.sourceforge.plantuml.ErrorUml;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.NullOutputStream;
import net.sourceforge.plantuml.OptionFlags;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.StringUtils;
import net.sourceforge.plantuml.utils.Base64Coder;
import net.sourceforge.plantuml.core.Diagram;
import net.sourceforge.plantuml.core.DiagramDescription;
import net.sourceforge.plantuml.core.ImageData;
import net.sourceforge.plantuml.error.PSystemError;
import net.sourceforge.plantuml.preproc.Defines;
import net.sourceforge.plantuml.version.Version;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Delegates the diagram generation from the UML source and the filling of the HTTP response with the diagram in the
 * right format. Its own responsibility is to produce the right HTTP headers.
 */
public class DiagramResponse {

    /**
     * X-Powered-By http header value included in every response by default.
     */
    private static final String POWERED_BY = "PlantUML Version " + Version.versionString();

    private static final List<String> CONFIG = new ArrayList<>();

    static {
        OptionFlags.ALLOW_INCLUDE = false;
        if ("true".equalsIgnoreCase(System.getenv("ALLOW_PLANTUML_INCLUDE"))) {
            OptionFlags.ALLOW_INCLUDE = true;
        }
    }

    /**
     * Response format.
     */
    private FileFormat format;
    /**
     * Http request.
     */
    private HttpServletRequest request;
    /**
     * Http response.
     */
    private HttpServletResponse response;


    private static final String WATERMARK_TEXT =
        "<?xml version=\"1.0\" encoding=\"us-ascii\" standalone=\"no\"?>\n"
        + "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
        + "contentStyleType=\"text/css\">"
        + "<g>"
            + "<text font-family=\"sans-serif\" font-size=\"12\" y=\"19\" x=\"19\" stroke-width=\"0\" fill=\"#bf0000\">"
            +   "<tspan>&#1042;&#1099; &#1080;&#1089;&#1087;&#1086;&#1083;&#1100;&#1079;&#1091;&#1077;&#1090;&#1077; "
            +       "&#1087;&#1091;&#1073;&#1083;&#1080;&#1095;&#1085;&#1099;&#1081; "
            +       "&#1090;&#1077;&#1089;&#1090;&#1086;&#1074;&#1099;&#1081; "
            +       "&#1089;&#1077;&#1088;&#1074;&#1077;&#1088;</tspan>"
            +   "<tspan x=\"19\" dy=\"1.2em\">&#1088;&#1077;&#1085;&#1076;&#1077;&#1088;&#1080;&#1085;&#1075;&#1072; "
            +       "&#1086;&#1090; &#1082;&#1086;&#1084;&#1072;&#1085;&#1076;&#1099; DocHub.</tspan>"
            +   "<tspan x=\"19\" dy=\"1.2em\">&#1045;&#1089;&#1083;&#1080; "
            +       "&#1074;&#1099; &#1087;&#1086;&#1085;&#1080;&#1084;&#1072;&#1077;&#1090;&#1077;, "
            +       "&#1095;&#1090;&#1086; "
            +       "&#1101;&#1090;&#1086; &#1090;&#1077;&#1089;&#1090;&#1086;&#1074;&#1099;&#1081;</tspan>"
            +   "<tspan x=\"19\" dy=\"1.2em\">&#1080; &#1087;&#1091;&#1073;&#1083;&#1080;&#1095;&#1085;&#1099;&#1081; "
            +       "&#1089;&#1077;&#1088;&#1074;&#1077;&#1088;,</tspan>"
            +   "<tspan x=\"19\" dy=\"1.2em\">&#1085;&#1086; &#1093;&#1086;&#1090;&#1080;&#1090;&#1077; "
            +       "&#1080;&#1089;&#1087;&#1086;&#1083;&#1100;&#1079;&#1086;&#1074;&#1072;&#1090;&#1100; "
            +       "&#1077;&#1075;&#1086; "
            +       "&#1076;&#1072;&#1083;&#1100;&#1096;&#1077; &#1073;&#1077;&#1079;</tspan>"
            +   "<tspan x=\"19\" dy=\"1.2em\">&#1101;&#1090;&#1086;&#1081; "
            +       "&#1085;&#1072;&#1076;&#1087;&#1080;&#1089;&#1080;,</tspan>"
            +   "<tspan x=\"19\" dy=\"1.2em\">&#1080;&#1079;&#1084;&#1077;&#1085;&#1080;&#1090;&#1077; &#1074; "
            +       "&#1085;&#1072;&#1089;&#1090;&#1088;&#1086;&#1081;&#1082;&#1072;&#1093;</tspan>"
            +   "<tspan x=\"19\" dy=\"1.2em\">&#1089;&#1089;&#1099;&#1083;&#1082;&#1091; &#1085;&#1072; "
            +       "&#1089;&#1077;&#1088;&#1074;&#1077;&#1088; "
            +       "&#1088;&#1077;&#1085;&#1076;&#1077;&#1088;&#1080;&#1085;&#1075;&#1072;:</tspan>"
            +   "<tspan x=\"19\" dy=\"1.2em\">"
            +       "http(s)://seaf.slsdev.ru/seafplantuml/iunderstandiusetestpublicplantuml/</tspan>"
            + "</text></g>"
            + "<g>"
            +   "<text transform=\"rotate(-45 200 212)\" opacity=\"0.2\" stroke=\"#000\" font-family=\"sans-serif\" "
            +   "font-size=\"67px\" y=\"50%\" x=\"50%\" text-anchor=\"middle\" dominant-baseline=\"central\" "
            +   "fill=\"#bf0000\">"
            +       "<tspan>&#1090;&#1077;&#1089;&#1090;&#1086;&#1074;&#1099;&#1081;</tspan>"
            +       "<tspan x=\"50%\" dy=\"-1.2em\">&#1087;&#1091;&#1073;&#1083;&#1080;&#1095;&#1085;&#1099;&#1081;"
            +       "</tspan>"
            +       "<tspan x=\"50%\" dy=\"2.4em\">&#1089;&#1077;&#1088;&#1074;&#1077;&#1088;</tspan>"
            + "</text></g></svg>";

    private static final Document WATERMARK_DOCUMENT;

    static {
        try {
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            try (InputStream watermarkInputStream = new ByteArrayInputStream(WATERMARK_TEXT.getBytes())) {
                WATERMARK_DOCUMENT = documentBuilder.parse(watermarkInputStream);
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create new diagram response instance.
     *
     * @param res http response
     * @param fmt target file format
     * @param req http request
     */
    public DiagramResponse(HttpServletResponse res, FileFormat fmt, HttpServletRequest req) {
        response = res;
        format = fmt;
        request = req;
    }

    /**
     * Render and send a specific uml diagram.
     *
     * @param uml textual UML diagram(s) source
     * @param idx diagram index of {@code uml} to send
     *
     * @throws IOException if an input or output exception occurred
     */
    public void sendDiagram(String uml, int idx, boolean addWatermark) throws IOException {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.setContentType(getContentType());

        if (CONFIG.size() == 0 && System.getenv("PLANTUML_CONFIG_FILE") != null) {
            // Read config
            final BufferedReader br = new BufferedReader(new FileReader(System.getenv("PLANTUML_CONFIG_FILE")));
            if (br == null) {
                return;
            }
            try {
                String s = null;
                while ((s = br.readLine()) != null) {
                    CONFIG.add(s);
                }
            } finally {
                br.close();
            }
        }

        SourceStringReader reader = new SourceStringReader(Defines.createEmpty(), uml, CONFIG);
        if (CONFIG.size() > 0 && reader.getBlocks().get(0).getDiagram().getWarningOrError() != null) {
            reader = new SourceStringReader(uml);
        }

        if (format == FileFormat.BASE64) {
            byte[] imageBytes;
            try (ByteArrayOutputStream outstream = new ByteArrayOutputStream()) {
                reader.outputImage(outstream, idx, new FileFormatOption(FileFormat.PNG));
                imageBytes = outstream.toByteArray();
            }
            final String base64 = Base64Coder.encodeLines(imageBytes).replaceAll("\\s", "");
            final String encodedBytes = "data:image/png;base64," + base64;
            response.getOutputStream().write(encodedBytes.getBytes());
            return;
        }
        final BlockUml blockUml = reader.getBlocks().get(0);
        if (notModified(blockUml)) {
            addHeaderForCache(blockUml);
            response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        if (StringUtils.isDiagramCacheable(uml)) {
            addHeaderForCache(blockUml);
        }
        final Diagram diagram = blockUml.getDiagram();
        if (diagram instanceof PSystemError) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        if (addWatermark) {
            try (ByteArrayOutputStream svgResult = new ByteArrayOutputStream()) {
                diagram.exportDiagram(svgResult, idx, new FileFormatOption(format));
                try {
                    DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                    try (ByteArrayInputStream svgInputStream = new ByteArrayInputStream(svgResult.toByteArray())) {
                        Document document = documentBuilder.parse(svgInputStream);
                        Node root = document.getDocumentElement();



                        int width = Integer.parseInt(root.getAttributes().getNamedItem("width").getNodeValue()
                            .replace("px", ""));
                        int height = Integer.parseInt(root.getAttributes().getNamedItem("height").getNodeValue()
                            .replace("px", ""));
                        if (width < 400) {
                            width = 400;
                        }
                        height = height + 150;
                        root.getAttributes().getNamedItem("width").setNodeValue(width + "px");
                        root.getAttributes().getNamedItem("height").setNodeValue(height + "px");
                        root.getAttributes().getNamedItem("viewBox")
                            .setNodeValue(String.format("0 0 %d %d", width, height));

                        NodeList svgChildren = root.getChildNodes();
                        Element g = null;
                        for (int i = 0; i < svgChildren.getLength(); ++i) {
                           if ("g".equals(svgChildren.item(i).getNodeName())) {
                               g = (Element) svgChildren.item(i);
                               break;
                           }
                        }
                        if (g != null) {
                            g.setAttribute("transform", "translate(0, 150)");
                            Node watermarkDisclaimer = WATERMARK_DOCUMENT.getDocumentElement().getFirstChild()
                                .cloneNode(true);
                            document.adoptNode(watermarkDisclaimer);
                            root.appendChild(watermarkDisclaimer);
                            Node watermarkBackground = WATERMARK_DOCUMENT.getDocumentElement().getChildNodes().item(1)
                                .cloneNode(true);
                            document.adoptNode(watermarkBackground);
                            Element watermarkBackgroundText = (Element) watermarkBackground.getFirstChild();
                            int xCenter = width >> 1;
                            int yCenter = height >> 1;
                            watermarkBackgroundText.getAttributes().getNamedItem("transform")
                                .setNodeValue(String.format("rotate(-45 %d %d)", xCenter, yCenter));
                            watermarkBackgroundText.getAttributes().getNamedItem("x").setNodeValue(xCenter + "px");
                            watermarkBackgroundText.getAttributes().getNamedItem("y").setNodeValue(yCenter + "px");
                            NodeList watermarkBackgroundSpans = watermarkBackgroundText.getChildNodes();
                            for (int i = 0; i < watermarkBackgroundSpans.getLength(); ++i) {
                                Node xAttribute = watermarkBackgroundSpans.item(i).getAttributes().getNamedItem("x");
                                if (xAttribute != null) {
                                    xAttribute.setNodeValue(xCenter + "px");
                                }
                            }

                            watermarkBackgroundText.getAttributes().getNamedItem("font-size")
                                .setNodeValue((Math.min(height, width) / 6) + "px");
                            root.insertBefore(watermarkBackground, g);
                        }

                        Transformer transformer = TransformerFactory.newInstance().newTransformer();
                        DOMSource source = new DOMSource(document);
                        transformer.transform(source, new StreamResult(response.getOutputStream()));
                    }
                } catch (ParserConfigurationException | SAXException | TransformerException e) {
                    svgResult.writeTo(response.getOutputStream());
                }
            }

        } else {
            diagram.exportDiagram(response.getOutputStream(), idx, new FileFormatOption(format));
        }
    }

    /**
     * Is block uml unmodified?
     *
     * @param blockUml block uml
     *
     * @return true if unmodified; otherwise false
     */
    private boolean notModified(BlockUml blockUml) {
        final String ifNoneMatch = request.getHeader("If-None-Match");
        final long ifModifiedSince = request.getDateHeader("If-Modified-Since");
        if (ifModifiedSince != -1 && ifModifiedSince != blockUml.lastModified()) {
            return false;
        }
        final String etag = blockUml.etag();
        if (ifNoneMatch == null) {
            return false;
        }
        return ifNoneMatch.contains(etag);
    }

    /**
     * Produce and send the image map of the uml diagram in HTML format.
     *
     * @param uml textual UML diagram source
     * @param idx diagram index of {@code uml} to send
     *
     * @throws IOException if an input or output exception occurred
     */
    public void sendMap(String uml, int idx) throws IOException {
        if (idx < 0) {
            idx = 0;
        }
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.setContentType(getContentType());
        SourceStringReader reader = new SourceStringReader(uml);
        final BlockUml blockUml = reader.getBlocks().get(0);
        if (StringUtils.isDiagramCacheable(uml)) {
            addHeaderForCache(blockUml);
        }
        final Diagram diagram = blockUml.getDiagram();
        ImageData map = diagram.exportDiagram(new NullOutputStream(), idx,
                new FileFormatOption(FileFormat.PNG, false));
        if (map.containsCMapData()) {
            PrintWriter httpOut = response.getWriter();
            final String cmap = map.getCMapData("plantuml");
            httpOut.print(cmap);
        }
    }

    /**
     * Check the syntax of the diagram and send a report in TEXT format.
     *
     * @param uml textual UML diagram source
     *
     * @throws IOException if an input or output exception occurred
     */
    public void sendCheck(String uml) throws IOException {
        response.setContentType(getContentType());
        SourceStringReader reader = new SourceStringReader(uml);
        DiagramDescription desc = reader.outputImage(
            new NullOutputStream(),
            new FileFormatOption(FileFormat.PNG, false)
        );
        PrintWriter httpOut = response.getWriter();
        httpOut.print(desc.getDescription());
    }

    /**
     * Add default header including cache headers to response.
     *
     * @param blockUml response block uml
     */
    private void addHeaderForCache(BlockUml blockUml) {
        long today = System.currentTimeMillis();
        // Add http headers to force the browser to cache the image
        final int maxAge = 3600 * 24 * 5;
        response.addDateHeader("Expires", today + 1000L * maxAge);
        response.addDateHeader("Date", today);

        response.addDateHeader("Last-Modified", blockUml.lastModified());
        response.addHeader("Cache-Control", "public, max-age=" + maxAge);
        // response.addHeader("Cache-Control", "max-age=864000");
        response.addHeader("Etag", "\"" + blockUml.etag() + "\"");
        final Diagram diagram = blockUml.getDiagram();
        response.addHeader("X-PlantUML-Diagram-Description", diagram.getDescription().getDescription());
        if (diagram instanceof PSystemError) {
            final PSystemError error = (PSystemError) diagram;
            for (ErrorUml err : error.getErrorsUml()) {
                response.addHeader("X-PlantUML-Diagram-Error", err.getError());
                response.addHeader("X-PlantUML-Diagram-Error-Line", "" + err.getLineLocation().getPosition());
            }
        }
        addHeaders(response);
    }

    /**
     * Add default headers to response.
     *
     * @param response http response
     */
    private static void addHeaders(HttpServletResponse response) {
        response.addHeader("X-Powered-By", POWERED_BY);
        response.addHeader("X-Patreon", "Support us on https://plantuml.com/patreon");
        response.addHeader("X-Donate", "https://plantuml.com/paypal");
    }

    /**
     * Get response content type.
     *
     * @return response content type
     */
    private String getContentType() {
        return format.getMimeType();
    }

}
