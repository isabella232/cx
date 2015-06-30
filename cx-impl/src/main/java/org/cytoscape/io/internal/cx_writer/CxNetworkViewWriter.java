package org.cytoscape.io.internal.cx_writer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.cytoscape.io.internal.cxio.Aspect;
import org.cytoscape.io.internal.cxio.AspectSet;
import org.cytoscape.io.internal.cxio.CxExporter;
import org.cytoscape.io.write.CyWriter;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.work.TaskMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CxNetworkViewWriter implements CyWriter {

    private final static Logger  logger       = LoggerFactory.getLogger(CxNetworkViewWriter.class);
    private final static String  ENCODING     = "UTF-8";
    public final static boolean  TIMING       = true;
    final static boolean         WRITE_TO_BAS = false;

    private final OutputStream   os;
    private final CyNetworkView  network_view;
    private final CharsetEncoder encoder;

    public CxNetworkViewWriter(final OutputStream os, final CyNetworkView network_view) {
        this.os = os;
        this.network_view = network_view;

        if (Charset.isSupported(ENCODING)) {
            // UTF-8 is supported by system
            this.encoder = Charset.forName(ENCODING).newEncoder();
        }
        else {
            // Use default.
            logger.warn("UTF-8 is not supported by this system.  This can be a problem for non-English annotations.");
            this.encoder = Charset.defaultCharset().newEncoder();
        }
    }

    @Override
    public void run(final TaskMonitor taskMonitor) throws Exception {
        if (taskMonitor != null) {
            taskMonitor.setProgress(0.0);
            taskMonitor.setTitle("Exporting to CX");
            taskMonitor.setStatusMessage("Exporting current network view as CX...");
        }

        System.out.println("Encoding = " + encoder.charset());

        final AspectSet aspects = new AspectSet();
        aspects.addAspect(Aspect.NODES);
        aspects.addAspect(Aspect.CARTESIAN_LAYOUT);
        aspects.addAspect(Aspect.EDGES);
        aspects.addAspect(Aspect.NODE_ATTRIBUTES);
        aspects.addAspect(Aspect.EDGE_ATTRIBUTES);

        // final AspectKeyFilter na_filter = new
        // AspectKeyFilterBasic(NodeAttributesElement.NAME);
        // final AspectKeyFilter ea_filter = new
        // AspectKeyFilterBasic(EdgeAttributesElement.NAME);

        // na_filter.addExcludeAspectKey("name");
        // ea_filter.addExcludeAspectKey("selected");
        // ea_filter.addExcludeAspectKey("SUID");
        // final Set<AspectKeyFilter> filters = new HashSet<AspectKeyFilter>();
        // filters.add(na_filter);
        // filters.add(ea_filter);

        final CxExporter exporter = CxExporter.createInstance();
        // exporter.writeCX(network_view, aspects, filters, os);

        final long t0 = System.currentTimeMillis();
        if (!WRITE_TO_BAS) {
            exporter.writeCX(network_view, aspects, os);
            os.close();
        }
        else {
            exporter.writeCX(network_view, aspects, new ByteArrayOutputStream());
        }

        if (TIMING) {
            CxExporter.reportTime(t0, "total time", 0);
        }
    }

    @Override
    public void cancel() {
        if (os == null) {
            return;
        }

        try {
            os.close();
        }
        catch (final IOException e) {
            logger.error("Could not close Outputstream for CxNetworkViewWriter.", e);
        }
    }

}
