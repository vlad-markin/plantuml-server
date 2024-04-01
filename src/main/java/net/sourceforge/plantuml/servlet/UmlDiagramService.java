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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.OptionFlags;
import net.sourceforge.plantuml.code.ArobaseStringCompressor;
import net.sourceforge.plantuml.code.CompressionZlib;
import net.sourceforge.plantuml.code.StringCompressor;
import net.sourceforge.plantuml.servlet.utility.UmlExtractor;
import net.sourceforge.plantuml.servlet.utility.UrlDataExtractor;
import org.eclipse.elk.alg.common.compaction.options.PolyominoOptions;
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider;
import org.eclipse.elk.core.data.LayoutMetaDataService;
import org.eclipse.elk.core.labels.LabelManagementOptions;

import javax.imageio.IIOException;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Objects;

/**
 * Common service servlet to produce diagram from compressed UML source contained in the end part of the requested URI.
 */
@SuppressWarnings("SERIAL")
public abstract class UmlDiagramService extends HttpServlet {

    static {
        OptionFlags.ALLOW_INCLUDE = false;
        if ("true".equalsIgnoreCase(System.getenv("ALLOW_PLANTUML_INCLUDE"))) {
            OptionFlags.ALLOW_INCLUDE = true;
        }
        final LayoutMetaDataService layoutMetaDataService = LayoutMetaDataService.getInstance();
        layoutMetaDataService.registerLayoutMetaDataProviders(new LayeredMetaDataProvider());
        layoutMetaDataService.registerLayoutMetaDataProviders(new PolyominoOptions());
        layoutMetaDataService.registerLayoutMetaDataProviders(new LabelManagementOptions());
    }

    private static final StringCompressor STRING_COMPRESSOR = new ArobaseStringCompressor();

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        final String url = request.getRequestURI();
        final String encoded = UrlDataExtractor.getEncodedDiagram(url, "");
        final int idx = UrlDataExtractor.getIndex(url, 0);

        // build the UML source from the compressed request parameter
        final String uml;
        try {
            uml = UmlExtractor.getUmlSource(encoded);
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad Request");
            return;
        }

        doDiagramResponse(request, response, uml, idx);
    }


    private String readDiagramFromRequestBody(HttpServletRequest request) throws IOException {
        // read textual diagram source from request body
        final StringBuilder uml = new StringBuilder();
        try (BufferedReader in = request.getReader()) {
            String line;
            while ((line = in.readLine()) != null) {
                uml.append(line).append('\n');
            }
        }

        return uml.toString();
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final int idx = UrlDataExtractor.getIndex(request.getRequestURI(), 0);
        final String compression = UrlDataExtractor.getEncodedDiagram(request.getRequestURI(), "").toLowerCase();

        String umlString = "@startuml\ntitle Unable to decode string\n@enduml";
        switch (compression) {
            case "compressed":
                umlString = UmlExtractor.getUmlSource(readDiagramFromRequestBody(request));
                break;
            case "zopfli":
                try {
                    umlString = (new CompressionZlib())
                        .decompress(request.getInputStream().readAllBytes())
                        .toUFT8String();
                    umlString = STRING_COMPRESSOR.decompress(umlString);
                } catch (Exception e) {
                    // return default umlString with error message
                }
                break;
            default:
                umlString = readDiagramFromRequestBody(request);
                umlString = STRING_COMPRESSOR.decompress(umlString);
        }

        doDiagramResponse(request,
                response,
                umlString,
                idx);
    }

    /**
     * Send diagram response.
     *
     * @param request html request
     * @param response html response
     * @param uml textual UML diagram(s) source
     * @param idx diagram index of {@code uml} to send
     *
     * @throws IOException if an input or output exception occurred
     */
    private void doDiagramResponse(
        HttpServletRequest request,
        HttpServletResponse response,
        String uml,
        int idx
    ) throws IOException {
        // generate the response
        DiagramResponse dr = new DiagramResponse(response, getOutputFormat(), request);
        try {
            boolean addWatermark = false;
            try {
                addWatermark = getOutputFormat() == FileFormat.SVG
                    && !Objects.equals(request.getRequestURI().split("/")[2], "iunderstandiusetestpublicplantuml");
            } catch (Exception ex) {
                // ignore
            }
            dr.sendDiagram(uml, idx, addWatermark);
        } catch (IIOException e) {
            // Browser has closed the connection, so the HTTP OutputStream is closed
            // Silently catch the exception to avoid annoying log
        }
    }

    /**
     * Gives the wished output format of the diagram. This value is used by the DiagramResponse class.
     *
     * @return the format
     */
    abstract public FileFormat getOutputFormat();

}
