package org.cytoscape.io.internal.cxio;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.cxio.aspects.datamodels.AttributesAspectUtils;
import org.cxio.aspects.datamodels.CartesianLayoutElement;
import org.cxio.aspects.datamodels.CyGroupsElement;
import org.cxio.aspects.datamodels.CyTableColumnElement;
import org.cxio.aspects.datamodels.CyViewsElement;
import org.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.cxio.aspects.datamodels.EdgeAttributesElement;
import org.cxio.aspects.datamodels.EdgesElement;
import org.cxio.aspects.datamodels.HiddenAttributesElement;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.aspects.datamodels.NetworkRelationsElement;
import org.cxio.aspects.datamodels.NodeAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.cxio.aspects.datamodels.SubNetworkElement;
import org.cxio.misc.AspectElementCounts;
import org.cxio.core.CxWriter;
import org.cxio.core.interfaces.AspectElement;
import org.cxio.core.interfaces.AspectFragmentWriter;
import org.cxio.filters.AspectKeyFilter;
import org.cxio.metadata.MetaDataCollection;
import org.cxio.metadata.MetaDataElement;
import org.cytoscape.group.CyGroup;
import org.cytoscape.group.CyGroupManager;
import org.cytoscape.io.cx.Aspect;
import org.cytoscape.io.internal.cx_writer.VisualPropertiesGatherer;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.vizmap.VisualMappingManager;

/**
 * This class is for serializing Cytoscape networks, views, and attribute tables
 * as CX formatted output streams. <br>
 * <br>
 * In particular, it provides the following methods for writing CX: <br>
 * <ul>
 * <li>
 * {@link #writeCX(CyNetwork, AspectSet, OutputStream)}</li>
 * <li>
 * {@link #writeCX(CyNetworkView, AspectSet, OutputStream)}</li>
 * <li>
 * {@link #writeNetwork(CyNetwork, AspectSet, FilterSet, OutputStream)}</li>
 * <li>
 * {@link #writeNetworkView(CyNetworkView, AspectSet, FilterSet, OutputStream)}</li>
 * </ul>
 * <br>
 * <br>
 * These methods use: <br>
 * <ul>
 * <li>
 * {@link AspectSet} to control which aspects to serialize</li>
 * <li>
 * {@link FilterSet} to control which aspect keys/fields to include within an
 * aspect</li>
 * </ul>
 * <br>
 *
 * @see AspectSet
 * @see Aspect
 * @see FilterSet
 * @see CxOutput
 * @see CxImporter
 *
 *
 */
public final class CxExporter {

    private final static boolean     DEFAULT_USE_DEFAULT_PRETTY_PRINTING      = true;

    private VisualLexicon            _lexicon;
    private boolean                  _use_default_pretty_printing;
    private VisualMappingManager     _visual_mapping_manager;
    private CyNetworkViewManager     _networkview_manager;
    private CyGroupManager           _group_manager;
    private boolean                  _write_pre_metdata;
    private boolean                  _write_post_metdata;
    private long                     _next_suid;

    private final static Set<String> ADDITIONAL_IGNORE_FOR_EDGE_ATTRIBUTES    = new HashSet<String>();
    private final static Set<String> ADDITIONAL_IGNORE_FOR_NODE_ATTRIBUTES    = new HashSet<String>();
    private final static Set<String> ADDITIONAL_IGNORE_FOR_NETWORK_ATTRIBUTES = new HashSet<String>();

    static {
        ADDITIONAL_IGNORE_FOR_EDGE_ATTRIBUTES.add(CxUtil.SHARED_INTERACTION);
        ADDITIONAL_IGNORE_FOR_NETWORK_ATTRIBUTES.add(CxUtil.SHARED_NAME_COL);
        ADDITIONAL_IGNORE_FOR_NODE_ATTRIBUTES.add(CxUtil.SHARED_NAME_COL);
        ADDITIONAL_IGNORE_FOR_NODE_ATTRIBUTES.add(CxUtil.REPRESENTS);
    }

    /**
     * This returns a new instance of CxExporter.
     *
     * @return a new CxExporter
     */
    public final static CxExporter createInstance() {
        return new CxExporter();
    }

    public void setGroupManager(final CyGroupManager group_manager) {
        _group_manager = group_manager;
    }

    public void setLexicon(final VisualLexicon lexicon) {
        _lexicon = lexicon;
    }

    public void setNetworkViewManager(final CyNetworkViewManager networkview_manager) {
        _networkview_manager = networkview_manager;

    }

    public final void setNextSuid(final long next_suid) {
        _next_suid = next_suid;
    }

    public void setUseDefaultPrettyPrinting(final boolean use_default_pretty_printing) {
        _use_default_pretty_printing = use_default_pretty_printing;
    }

    public void setVisualMappingManager(final VisualMappingManager visual_mapping_manager) {
        _visual_mapping_manager = visual_mapping_manager;
    }

    public final void setWritePostMetadata(final boolean write_post_metdata) {
        _write_post_metdata = write_post_metdata;
    }

    public final void setWritePreMetadata(final boolean write_pre_metdata) {
        _write_pre_metdata = write_pre_metdata;
    }

    /**
     * This is a method for serializing a Cytoscape network and associated table
     * data as CX formatted OutputStream. <br>
     * Method arguments control which aspects to serialize, and for data stored
     * in node and tables (serialized as node attributes and edge attributes
     * aspects), which table columns to include or exclude.
     *
     *
     * @param network
     *            the CyNetwork, and by association, tables to be serialized
     * @param aspects
     *            the set of aspects to serialize
     * @param filters
     *            the set of filters controlling which node and edge table
     *            columns to include or exclude
     * @param out
     *            the stream to write to
     * @return a CxOutput object which contains the output stream as well as a
     *         status
     * @throws IOException
     *
     *
     * @see AspectSet
     * @see Aspect
     * @see FilterSet
     *
     */
    public final boolean writeNetwork(final CyNetwork network,
                                      final boolean write_siblings,
                                      final AspectSet aspects,
                                      final FilterSet filters,
                                      final OutputStream out) throws IOException {

        if (!aspects.contains(Aspect.SUBNETWORKS)) {
            if (aspects.contains(Aspect.VISUAL_PROPERTIES)) {
                throw new IllegalArgumentException("need to write sub-networks in order to write visual properties");
            }
            if (aspects.contains(Aspect.CARTESIAN_LAYOUT)) {
                throw new IllegalArgumentException("need to write sub-networks in order to write cartesian layout");
            }
        }

        final CxWriter w = CxWriter.createInstance(out, _use_default_pretty_printing);

        if ((filters != null) && !filters.getFilters().isEmpty()) {
            addAspectFragmentWriters(w, aspects.getAspectFragmentWriters(), filters.getFiltersAsMap());
        }
        else {
            addAspectFragmentWriters(w, aspects.getAspectFragmentWriters());
        }

        if (_write_pre_metdata) {
            addPreMetadata(aspects, network, write_siblings, w, 1L);
        }

        w.start();

        String msg = null;
        boolean success = true;

        try {
            if (aspects.contains(Aspect.NODES)) {
                writeNodes(network, write_siblings, w);
            }
            if (aspects.contains(Aspect.EDGES)) {
                writeEdges(network, write_siblings, w);
            }
            if (aspects.contains(Aspect.TABLE_COLUMN_LABELS)) {
            	writeTableColumnLabels(network, write_siblings, w );
            }
            if (aspects.contains(Aspect.NETWORK_ATTRIBUTES)) {
                writeNetworkAttributes(network, write_siblings, w, CyNetwork.DEFAULT_ATTRS);
            }
            if (aspects.contains(Aspect.HIDDEN_ATTRIBUTES)) {
                writeHiddenAttributes(network, write_siblings, w, CyNetwork.HIDDEN_ATTRS);
            }
            if (aspects.contains(Aspect.NODE_ATTRIBUTES)) {
                writeNodeAttributes(network, write_siblings, w, CyNetwork.DEFAULT_ATTRS);
            }
            if (aspects.contains(Aspect.EDGE_ATTRIBUTES)) {
                writeEdgeAttributes(network, write_siblings, w, CyNetwork.DEFAULT_ATTRS);
            }
            if (aspects.contains(Aspect.SUBNETWORKS)) {
                writeSubNetworks(network, write_siblings, w, aspects);
            }
            if (aspects.contains(Aspect.VIEWS) || aspects.contains(Aspect.NETWORK_RELATIONS)) {
                writeNetworkViews(network, write_siblings, w);
                writeNetworkRelations(network, write_siblings, w, true);
            }
            if (aspects.contains(Aspect.GROUPS)) {
                writeGroups(network, write_siblings, w);
            }
            if (_write_post_metdata) {
                final AspectElementCounts aspects_counts = w.getAspectElementCounts();
                addPostMetadata(aspects, network, w, 1L, aspects_counts);
            }
        } catch (final Exception e) {
            e.printStackTrace();
            msg = e.getMessage();
            success = false;
        }

        w.end(success, msg);

        if (success) {
            final AspectElementCounts counts = w.getAspectElementCounts();
            if (counts != null) {
                System.out.println("Aspects elements written out:");
                System.out.println(counts);
            }
        }

        return success;

    }

    /**
     * This is a method for serializing a Cytoscape network and associated table
     * data as CX formatted OutputStream. <br>
     * Method arguments control which aspects to serialize.
     *
     *
     * @param network
     *            the CyNetwork, and by association, tables to be serialized
     * @param aspects
     *            the set of aspects to serialize
     * @param out
     *            the stream to write to
     * @throws IOException
     *
     *
     * @see AspectSet
     * @see Aspect
     *
     */
    public final boolean writeNetwork(final CyNetwork network,
                                      final boolean write_siblings,
                                      final AspectSet aspects,
                                      final OutputStream out) throws IOException {
        // Filters are not being used, thus null.
        return writeNetwork(network, write_siblings, aspects, null, out);
    }

    private final static void addDataToMetaDataCollection(final MetaDataCollection pre_meta_data,
                                                          final String aspect_name,
                                                          final Long consistency_group,
                                                          final Long id_counter,
                                                          final Long counter,
                                                          final String key,
                                                          final String value) {
        pre_meta_data.setElementCount(aspect_name, counter);
        pre_meta_data.setVersion(aspect_name, "1.0");
        if ((key != null) && (key.length() > 0)) {
            pre_meta_data.setProperty(aspect_name, key, value);
        }
        pre_meta_data.setConsistencyGroup(aspect_name, consistency_group);
        if (id_counter > -Long.MAX_VALUE) {
            pre_meta_data.setIdCounter(aspect_name, id_counter);
        }
    }
    
    private final static void addDataToPreMetaDataCollection(final MetaDataCollection pre_meta_data,
                                                          final String aspect_name,
                                                          final Long consistency_group,
                                                          final String key,
                                                          final String value) {
        pre_meta_data.setVersion(aspect_name, "1.0");
        if ((key != null) && (key.length() > 0)) {
            pre_meta_data.setProperty(aspect_name, key, value);
        }
        pre_meta_data.setConsistencyGroup(aspect_name, consistency_group);
    }

	private final static void addDataToPostMetaDataCollection(final MetaDataCollection pre_meta_data,
			final String aspect_name, final Long counter) {
		final MetaDataElement element = new MetaDataElement(new TreeMap<String, Object>());
		element.setName(aspect_name);
		element.setElementCount(counter);
		pre_meta_data.add(element);
	}

    private final static String getInteractionFromEdgeTable(final CyNetwork network, final CyEdge edge) {
        final CyRow row = network.getTable(CyEdge.class, CyNetwork.DEFAULT_ATTRS).getRow(edge.getSUID());
        if (row != null) {
            final Object o = row.getRaw(CxUtil.SHARED_INTERACTION);
            if ((o != null) && (o instanceof String)) {
                return String.valueOf(o);
            }
        }
        return null;
    }

    private final static String getRepresentsFromNodeTable(final CyNetwork network, final CyNode node) {
        final CyRow row = network.getTable(CyNode.class, CyNetwork.DEFAULT_ATTRS).getRow(node.getSUID());
        if (row != null) {
            final Object o = row.getRaw(CxUtil.REPRESENTS);
            if ((o != null) && (o instanceof String)) {
                return String.valueOf(o);
            }
        }
        return null;
    }

    private final static String getSharedNameFromNodeTable(final CyNetwork network, final CyNode node) {
        final CyRow row = network.getTable(CyNode.class, CyNetwork.DEFAULT_ATTRS).getRow(node.getSUID());
        if (row != null) {
            final Object o = row.getRaw(CxUtil.SHARED_NAME_COL);
            if ((o != null) && (o instanceof String)) {
                return String.valueOf(o);
            }
        }
        return null;
    }

    private final static List<CySubNetwork> makeSubNetworkList(final boolean write_siblings,
                                                               final CySubNetwork sub_network,
                                                               final CyRootNetwork root,
                                                               final boolean ignore_nameless_sub_networks) {
        List<CySubNetwork> subnets = new ArrayList<CySubNetwork>();

        if (write_siblings) {
            for (final CySubNetwork s : root.getSubNetworkList()) {
                if (!ignore_nameless_sub_networks || (getSubNetworkName(s) != null)) {
                    subnets.add(s);
                }
            }
        }
        else {
            subnets = new ArrayList<CySubNetwork>();
            if (!ignore_nameless_sub_networks || (getSubNetworkName(sub_network) != null)) {
                subnets.add(sub_network);
            }
        }
        return subnets;
    }

    private final static void writeCartesianLayout(final CyNetworkView view, final CxWriter w) throws IOException {
        final CyNetwork network = view.getModel();
        final List<AspectElement> elements = new ArrayList<AspectElement>();

        boolean z_used = false;
        for (final CyNode cy_node : network.getNodeList()) {
            final View<CyNode> node_view = view.getNodeView(cy_node);
            if (Math.abs(node_view.getVisualProperty(BasicVisualLexicon.NODE_Z_LOCATION)) > 0.000000001) {
                z_used = true;
                break;
            }
        }

        for (final CyNode cy_node : network.getNodeList()) {
            final View<CyNode> node_view = view.getNodeView(cy_node);
            if (z_used) {
                elements.add(new CartesianLayoutElement(cy_node.getSUID(), view.getSUID(), node_view
                        .getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION), node_view
                                                        .getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION), node_view
                                                        .getVisualProperty(BasicVisualLexicon.NODE_Z_LOCATION)));
            }
            else {
                elements.add(new CartesianLayoutElement(cy_node.getSUID(), view.getSUID(), node_view
                        .getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION), node_view
                                                        .getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION)));
            }

        }
        final long t0 = System.currentTimeMillis();
        w.writeAspectElements(elements);
        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "cartesian layout", elements.size());
        }
    }

    private final static void writeEdges(final CyNetwork network, final boolean write_siblings, final CxWriter w)
            throws IOException {
        final List<AspectElement> elements = new ArrayList<AspectElement>();
        final CyRootNetwork my_root = ((CySubNetwork) network).getRootNetwork();
        if (write_siblings) {
            for (final CyEdge cy_edge : my_root.getEdgeList()) {
                elements.add(new EdgesElement(cy_edge.getSUID(), cy_edge.getSource().getSUID(), cy_edge.getTarget()
                                              .getSUID(), getInteractionFromEdgeTable(my_root, cy_edge)));
            }
        }
        else {
            for (final CyEdge cy_edge : ((CySubNetwork) network).getEdgeList()) {
                elements.add(new EdgesElement(cy_edge.getSUID(), cy_edge.getSource().getSUID(), cy_edge.getTarget()
                                              .getSUID(), getInteractionFromEdgeTable(network, cy_edge)));
            }
        }
        final long t0 = System.currentTimeMillis();
        w.writeAspectElements(elements);
        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "edges", elements.size());
        }
    }

	private final void writeNetworkRelations(
			final CyNetwork network, final boolean write_siblings, final CxWriter w,
			final boolean ignore_nameless_sub_networks) throws IOException {
		
		final CySubNetwork as_subnet = (CySubNetwork) network;
		final CyRootNetwork root = as_subnet.getRootNetwork();
		final List<CySubNetwork> subnetworks = makeSubNetworkList(write_siblings, as_subnet, root, true);

		final List<AspectElement> elements = new ArrayList<AspectElement>();

		for (final CySubNetwork subnetwork : subnetworks) {

			final String name = getSubNetworkName(subnetwork);
			if (ignore_nameless_sub_networks && (name == null)) {
				continue;
			}
			
			// Subnetworks does not need root ID since it's not used in CX.
			final NetworkRelationsElement rel_subnet = new NetworkRelationsElement(null, subnetwork.getSUID(),
					NetworkRelationsElement.TYPE_SUBNETWORK, name);
			// PLEASE NOTE:
			// Cytoscape currently has only one view per sub-network.
			final Collection<CyNetworkView> views = _networkview_manager.getNetworkViews(subnetwork);
			for (final CyNetworkView view : views) {
				final NetworkRelationsElement rel_view = new NetworkRelationsElement(subnetwork.getSUID(),
						view.getSUID(), NetworkRelationsElement.TYPE_VIEW, name + " view");
				elements.add(rel_view);
			}
			elements.add(rel_subnet);

		}
		final long t0 = System.currentTimeMillis();
		w.writeAspectElements(elements);
		if (Settings.INSTANCE.isTiming()) {
			TimingUtil.reportTimeDifference(t0, "network relations", elements.size());
		}

	}

    public static String getSubNetworkName(final CySubNetwork subnetwork) {
        final CyRow row = subnetwork.getRow(subnetwork, CyNetwork.DEFAULT_ATTRS);
        String name = null;
        final Map<String, Object> values = row.getAllValues();
        if ((values != null) && !values.isEmpty()) {
            if (values.get(CxUtil.NAME_COL) != null) {
                final String str = String.valueOf(values.get(CxUtil.NAME_COL));
                if ((str != null) && (str.trim().length() > 0)) {
                    name = str;
                }
            }
        }
        return name;
    }

    private final void writeNetworkViews(final CyNetwork network, final boolean write_siblings, final CxWriter w)
            throws IOException {
        final CySubNetwork my_subnet = (CySubNetwork) network;
        final CyRootNetwork root = my_subnet.getRootNetwork();
        final List<CySubNetwork> subnetworks = makeSubNetworkList(write_siblings, my_subnet, root, true);

        final List<AspectElement> elements = new ArrayList<AspectElement>();

        for (final CySubNetwork subnetwork : subnetworks) {
            // PLEASE NOTE:
            // Cytoscape currently has only one view per sub-network.
            final Collection<CyNetworkView> views = _networkview_manager.getNetworkViews(subnetwork);
            for (final CyNetworkView view : views) {
                final CyViewsElement view_element = new CyViewsElement(view.getSUID(), subnetwork.getSUID());
                elements.add(view_element);
            }
        }
        final long t0 = System.currentTimeMillis();
        w.writeAspectElements(elements);
        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "views", elements.size());
        }

    }

    private final static void writeNodes(final CyNetwork network, final boolean write_siblings, final CxWriter w)
            throws IOException {
        final List<AspectElement> elements = new ArrayList<AspectElement>();
        final CySubNetwork my_subnet = (CySubNetwork) network;
        final CyRootNetwork my_root = my_subnet.getRootNetwork();
        if (write_siblings) {
            for (final CyNode cy_node : my_root.getNodeList()) {
                elements.add(new NodesElement(cy_node.getSUID(),
                                              getSharedNameFromNodeTable(my_root, cy_node),
                                              getRepresentsFromNodeTable(my_root, cy_node)));
            }
        }
        else {
            for (final CyNode cy_node : my_subnet.getNodeList()) {
                elements.add(new NodesElement(cy_node.getSUID(),
                                              getSharedNameFromNodeTable(my_root, cy_node),
                                              getRepresentsFromNodeTable(network, cy_node)));
            }
        }
        final long t0 = System.currentTimeMillis();
        w.writeAspectElements(elements);
        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "nodes", elements.size());
        }
    }

    private final static void writeVisualProperties(final CyNetworkView view,
                                                    final VisualMappingManager visual_mapping_manager,
                                                    final VisualLexicon lexicon,
                                                    final CxWriter w) throws IOException {
        final CyNetwork network = view.getModel();
        final Set<VisualPropertyType> types = new HashSet<VisualPropertyType>();
        types.add(VisualPropertyType.NETWORK);
        types.add(VisualPropertyType.NODES);
        types.add(VisualPropertyType.EDGES);
        types.add(VisualPropertyType.NODES_DEFAULT);
        types.add(VisualPropertyType.EDGES_DEFAULT);

        final List<AspectElement> elements = VisualPropertiesGatherer
                .gatherVisualPropertiesAsAspectElements(view, network, visual_mapping_manager, lexicon, types);

        final long t0 = System.currentTimeMillis();
        w.writeAspectElements(elements);
        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "visual properties", elements.size());
        }
    }

    private final void addAspectFragmentWriters(final CxWriter w, final Set<AspectFragmentWriter> writers) {
        for (final AspectFragmentWriter writer : writers) {
            w.addAspectFragmentWriter(writer);
        }
    }

    private final void addAspectFragmentWriters(final CxWriter w,
                                                final Set<AspectFragmentWriter> writers,
                                                final SortedMap<String, AspectKeyFilter> filters) {
        for (final AspectFragmentWriter writer : writers) {
            if (filters != null) {
                final String aspect = writer.getAspectName();
                if (filters.containsKey(aspect)) {
                    writer.addAspectKeyFilter(filters.get(aspect));
                }
            }
            w.addAspectFragmentWriter(writer);
        }
    }

	private final void addPostMetadata(final AspectSet aspects, final CyNetwork network, final CxWriter w,
			final Long consistency_group, final AspectElementCounts aspects_counts) {

		final long t0 = System.currentTimeMillis();
		final MetaDataCollection post_meta_data = new MetaDataCollection();

		if (aspects.contains(Aspect.TABLE_COLUMN_LABELS)) {
			addDataToPostMetaDataCollection(post_meta_data, CyTableColumnElement.ASPECT_NAME,
					(long) aspects_counts.getAspectElementCount(CyTableColumnElement.ASPECT_NAME));
		}

		if (aspects.contains(Aspect.NETWORK_ATTRIBUTES)) {
			addDataToPostMetaDataCollection(post_meta_data, NetworkAttributesElement.ASPECT_NAME,
					(long) aspects_counts.getAspectElementCount(NetworkAttributesElement.ASPECT_NAME));
		}
		if (aspects.contains(Aspect.HIDDEN_ATTRIBUTES)) {
			addDataToPostMetaDataCollection(post_meta_data, HiddenAttributesElement.ASPECT_NAME,
					(long) aspects_counts.getAspectElementCount(HiddenAttributesElement.ASPECT_NAME));
		}
		if (aspects.contains(Aspect.NODE_ATTRIBUTES)) {
			addDataToPostMetaDataCollection(post_meta_data, NodeAttributesElement.ASPECT_NAME,
					(long) aspects_counts.getAspectElementCount(NodeAttributesElement.ASPECT_NAME));
		}
		if (aspects.contains(Aspect.EDGE_ATTRIBUTES)) {
			addDataToPostMetaDataCollection(post_meta_data, EdgeAttributesElement.ASPECT_NAME,
					(long) aspects_counts.getAspectElementCount(EdgeAttributesElement.ASPECT_NAME));
		}

		if (aspects.contains(Aspect.CARTESIAN_LAYOUT)) {
			addDataToPostMetaDataCollection(post_meta_data, CartesianLayoutElement.ASPECT_NAME,
					(long) aspects_counts.getAspectElementCount(CartesianLayoutElement.ASPECT_NAME));
		}

		if (aspects.contains(Aspect.VISUAL_PROPERTIES)) {
			addDataToPostMetaDataCollection(post_meta_data, CyVisualPropertiesElement.ASPECT_NAME,
					(long) aspects_counts.getAspectElementCount(CyVisualPropertiesElement.ASPECT_NAME));
		}
		if (aspects.contains(Aspect.SUBNETWORKS)) {
			addDataToPostMetaDataCollection(post_meta_data, SubNetworkElement.ASPECT_NAME,
					(long) aspects_counts.getAspectElementCount(SubNetworkElement.ASPECT_NAME));
		}
		if (aspects.contains(Aspect.VIEWS)) {
			addDataToPostMetaDataCollection(post_meta_data, CyViewsElement.ASPECT_NAME,
					(long) aspects_counts.getAspectElementCount(CyViewsElement.ASPECT_NAME));
		}
		if (aspects.contains(Aspect.GROUPS)) {
			addDataToPostMetaDataCollection(post_meta_data, CyGroupsElement.ASPECT_NAME,
					(long) aspects_counts.getAspectElementCount(CyGroupsElement.ASPECT_NAME)

			);
		}
		if (aspects.contains(Aspect.NETWORK_RELATIONS)) {
			addDataToPostMetaDataCollection(post_meta_data, NetworkRelationsElement.ASPECT_NAME,
					(long) aspects_counts.getAspectElementCount(NetworkRelationsElement.ASPECT_NAME)

			);
		}

		w.addPostMetaData(post_meta_data);

		if (Settings.INSTANCE.isTiming()) {
			TimingUtil.reportTimeDifference(t0, "post meta-data", -1);
		}

	}

    private final void addPreMetadata(final AspectSet aspects,
                                      final CyNetwork network,
                                      final boolean write_siblings,
                                      final CxWriter w,
                                      final Long consistency_group) {

        final CySubNetwork my_subnet = (CySubNetwork) network;
        final long t0 = System.currentTimeMillis();
        final MetaDataCollection pre_meta_data = new MetaDataCollection();

        CyNetwork my_network;
        if (write_siblings) {
            my_network = my_subnet.getRootNetwork();
        }
        else {
            my_network = my_subnet;
        }

        if (aspects.contains(Aspect.NODES)) {

            addDataToMetaDataCollection(pre_meta_data,
                                        NodesElement.ASPECT_NAME,
                                        consistency_group,
                                        _next_suid,
                                        (long) my_network.getNodeList().size(),
                                        "",
                                        "");
        }
        if (aspects.contains(Aspect.EDGES)) {

            addDataToMetaDataCollection(pre_meta_data,
                                        EdgesElement.ASPECT_NAME,
                                        consistency_group,
                                        _next_suid,
                                        (long) my_network.getEdgeList().size(),
                                        "",
                                        "");
        }
        
        if (aspects.contains(Aspect.TABLE_COLUMN_LABELS)) {
            addDataToPreMetaDataCollection(pre_meta_data,
            								CyTableColumnElement.ASPECT_NAME,
                                        consistency_group,
                                        "",
                                        "");
        }
        
        if (aspects.contains(Aspect.NETWORK_ATTRIBUTES)) {
            addDataToPreMetaDataCollection(pre_meta_data,
                                        NetworkAttributesElement.ASPECT_NAME,
                                        consistency_group,
                                        "",
                                        "");
        }
        if (aspects.contains(Aspect.HIDDEN_ATTRIBUTES)) {
            addDataToPreMetaDataCollection(pre_meta_data,
                                        HiddenAttributesElement.ASPECT_NAME,
                                        consistency_group,
                                        "",
                                        "");
        }
        if (aspects.contains(Aspect.NODE_ATTRIBUTES)) {
            addDataToPreMetaDataCollection(pre_meta_data,
                                        NodeAttributesElement.ASPECT_NAME,
                                        consistency_group,
                                        "",
                                        "");
        }
        if (aspects.contains(Aspect.EDGE_ATTRIBUTES)) {
            addDataToPreMetaDataCollection(pre_meta_data,
                                        EdgeAttributesElement.ASPECT_NAME,
                                        consistency_group,
                                        "",
                                        "");
        }

        if (aspects.contains(Aspect.CARTESIAN_LAYOUT)) {
            addDataToPreMetaDataCollection(pre_meta_data,
                                        CartesianLayoutElement.ASPECT_NAME,
                                        consistency_group,
                                        "",
                                        "");
        }

        if (aspects.contains(Aspect.VISUAL_PROPERTIES)) {
            addDataToPreMetaDataCollection(pre_meta_data,
                                        CyVisualPropertiesElement.ASPECT_NAME,
                                        consistency_group,
                                        "",
                    "");
        }
        if (aspects.contains(Aspect.SUBNETWORKS)) {
            addDataToPreMetaDataCollection(pre_meta_data,
                                        SubNetworkElement.ASPECT_NAME,
                                        consistency_group,
                                        "",
                    "");
        }
        if (aspects.contains(Aspect.VIEWS)) {
            addDataToPreMetaDataCollection(pre_meta_data,
                                        CyViewsElement.ASPECT_NAME,
                                        consistency_group,
                                        "",
                    "");
        }
        if (aspects.contains(Aspect.GROUPS)) {
            addDataToPreMetaDataCollection(pre_meta_data,
                                        CyGroupsElement.ASPECT_NAME,
                                        consistency_group,
                                        "",
                    "");
        }
        if (aspects.contains(Aspect.NETWORK_RELATIONS)) {
            addDataToPreMetaDataCollection(pre_meta_data,
                                        NetworkRelationsElement.ASPECT_NAME,
                                        consistency_group,
                                        "",
                                        "");
        }

        w.addPreMetaData(pre_meta_data);

        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "pre meta-data", -1);
        }

    }

    private final void writeEdgeAttributes(final CyNetwork network,
                                           final boolean write_siblings,
                                           final CxWriter w,
                                           final String namespace) throws IOException {

        final List<AspectElement> elements = new ArrayList<AspectElement>();

        final CySubNetwork my_subnet = (CySubNetwork) network;
        final CyRootNetwork my_root = my_subnet.getRootNetwork();
        final List<CySubNetwork> subnets = makeSubNetworkList(write_siblings, my_subnet, my_root, true);

        for (final CySubNetwork subnet : subnets) {
            writeEdgeAttributesHelper(namespace, subnet, subnet.getEdgeList(), elements);
        }

        final long t0 = System.currentTimeMillis();
        w.writeAspectElements(elements);
        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "edge attributes", elements.size());
        }
    }

    private final static boolean isIgnore(final String column_name,
                                          final Set<String> additional_to_ignore,
                                          final Settings setttings) {
        if (setttings.isIgnoreSuidColumn() && column_name.equals(CxUtil.SUID)) {
            return true;
        }
        else if (setttings.isIgnoreSelectedColumn() && column_name.equals(CxUtil.SELECTED)) {
            return true;
        }
        else if ((additional_to_ignore != null) && additional_to_ignore.contains(column_name)) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("rawtypes")
    private void writeEdgeAttributesHelper(final String namespace,
                                           final CyNetwork my_network,
                                           final List<CyEdge> edges,
                                           final List<AspectElement> elements) {

        for (final CyEdge cy_edge : edges) {
            final CyRow row = my_network.getRow(cy_edge, namespace);
            if (row != null) {

                final Map<String, Object> values = row.getAllValues();
                if ((values != null) && !values.isEmpty()) {
                    for (final String column_name : values.keySet()) {
                        if (isIgnore(column_name, ADDITIONAL_IGNORE_FOR_EDGE_ATTRIBUTES, Settings.INSTANCE)) {
                            continue;
                        }
                        final Object value = values.get(column_name);
                        if (value == null) {
                            continue;
                        }
                        EdgeAttributesElement e = null;
                        final long subnet = my_network.getSUID();
                        if (value instanceof List) {
                            final List<String> attr_values = new ArrayList<String>();
                            for (final Object v : (List) value) {
                                attr_values.add(String.valueOf(v));
                            }
                            if (!attr_values.isEmpty()) {
                                e = new EdgeAttributesElement(subnet,
                                                              cy_edge.getSUID(),
                                                              column_name,
                                                              attr_values,
                                                              AttributesAspectUtils.determineDataType(value));
                            }
                        }
                        else {
                            e = new EdgeAttributesElement(subnet,
                                                          cy_edge.getSUID(),
                                                          column_name,
                                                          String.valueOf(value),
                                                          AttributesAspectUtils.determineDataType(value));
                        }
                        if (e != null) {
                            elements.add(e);
                        }
                    }
                }
            }
        }
    }

    private final void writeGroups(final CyNetwork network, final boolean write_siblings, final CxWriter w)
            throws IOException {
        final CySubNetwork my_subnet = (CySubNetwork) network;
        final CyRootNetwork my_root = my_subnet.getRootNetwork();

        final List<CySubNetwork> subnets = makeSubNetworkList(write_siblings, my_subnet, my_root, true);

        final List<AspectElement> elements = new ArrayList<AspectElement>();
        for (final CySubNetwork subnet : subnets) {
            final Collection<CyNetworkView> views = _networkview_manager.getNetworkViews(subnet);
            if ((views == null) || (views.size() < 1)) {
                continue;
            }
            if (views.size() > 1) {
                System.out.println("multiple views for sub-network " + subnet + ", problem with attaching groups");
                continue;
            }
            Long view_id = 0L;
            for (final CyNetworkView view : views) {
                view_id = view.getSUID();
            }

            final Set<CyGroup> groups = _group_manager.getGroupSet(subnet);
            for (final CyGroup group : groups) {
                String name = null;
                final CyRow row = my_root.getRow(group.getGroupNode(), CyNetwork.DEFAULT_ATTRS);
                if (row != null) {
                    name = row.get(CxUtil.SHARED_NAME_COL, String.class);
                }
                if ((name == null) || (name.length() < 1)) {
                    name = "group " + group.getGroupNode().getSUID();
                }
                final CyGroupsElement group_element = new CyGroupsElement(group.getGroupNode().getSUID(), view_id, name);
                for (final CyEdge e : group.getExternalEdgeList()) {
                    group_element.addExternalEdge(e.getSUID());
                }
                for (final CyEdge e : group.getInternalEdgeList()) {
                    group_element.addInternalEdge(e.getSUID());
                }
                for (final CyNode e : group.getNodeList()) {
                    group_element.addNode(e.getSUID());
                }
                elements.add(group_element);
            }

        }
        final long t0 = System.currentTimeMillis();
        w.writeAspectElements(elements);
        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "groups", elements.size());
        }

    }

    private final void writeHiddenAttributes(final CyNetwork network,
                                             final boolean write_siblings,
                                             final CxWriter w,
                                             final String namespace) throws IOException {

        final List<AspectElement> elements = new ArrayList<AspectElement>();

        final CySubNetwork my_subnet = (CySubNetwork) network;
        final CyRootNetwork my_root = my_subnet.getRootNetwork();
        final List<CySubNetwork> subnets = makeSubNetworkList(write_siblings, my_subnet, my_root, true);

        for (final CySubNetwork subnet : subnets) {
            writeHiddenAttributesHelper(namespace, subnet, elements);
        }

        final long t0 = System.currentTimeMillis();
        w.writeAspectElements(elements);
        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "network attributes", elements.size());
        }
    }

    @SuppressWarnings("rawtypes")
    private void writeHiddenAttributesHelper(final String namespace,
                                             final CyNetwork my_network,
                                             final List<AspectElement> elements) {

        final CyRow row = my_network.getRow(my_network, namespace);
        if (row != null) {
            final Map<String, Object> values = row.getAllValues();

            if ((values != null) && !values.isEmpty()) {
                for (final String column_name : values.keySet()) {
                    if (isIgnore(column_name, null, Settings.INSTANCE)) {
                        continue;
                    }
                    final Object value = values.get(column_name);
                    if (value == null) {
                        continue;
                    }
                    HiddenAttributesElement e = null;
                    final long subnet = my_network.getSUID();
                    if (value instanceof List) {
                        final List<String> attr_values = new ArrayList<String>();
                        for (final Object v : (List) value) {
                            attr_values.add(String.valueOf(v));
                        }
                        if (!attr_values.isEmpty()) {
                            e = new HiddenAttributesElement(subnet,
                                                            column_name,
                                                            attr_values,
                                                            AttributesAspectUtils.determineDataType(value));
                        }
                    }
                    else {
                        e = new HiddenAttributesElement(subnet,
                                                        column_name,
                                                        String.valueOf(value),
                                                        AttributesAspectUtils.determineDataType(value));
                    }
                    if (e != null) {
                        elements.add(e);
                    }
                }
            }

        }
    }

    private final void writeNetworkAttributes(final CyNetwork network,
                                              final boolean write_siblings,
                                              final CxWriter w,
                                              final String namespace) throws IOException {

        final List<AspectElement> elements = new ArrayList<AspectElement>();
        
        final CySubNetwork my_subnet = (CySubNetwork) network;
        final CyRootNetwork my_root = my_subnet.getRootNetwork();

        final String collection_name = obtainNetworkCollectionName(my_root);
        final List<CySubNetwork> subnets = makeSubNetworkList(write_siblings, my_subnet, my_root, true);
        if (Settings.INSTANCE.isDebug()) {
            System.out.println("collection name: " + collection_name);
        }
        
        // Write root table
        writeNetworkAttributesHelper(namespace, my_root, elements);

        for (final CySubNetwork subnet : subnets) {
            writeNetworkAttributesHelper(namespace, subnet, elements);
        }

        final long t0 = System.currentTimeMillis();
        w.writeAspectElements(elements);
        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "network attributes", elements.size());
        }
    }

    public final static String obtainNetworkCollectionName(final CyRootNetwork root_network) {
        String collection_name = null;
        if (root_network != null) {
            final CyRow row = root_network.getRow(root_network, CyNetwork.DEFAULT_ATTRS);
            if (row != null) {
                try {
                    collection_name = String.valueOf(row.getRaw("name"));
                }
                catch (final Exception e) {
                    collection_name = null;
                }
            }
        }
        return collection_name;
    }

    @SuppressWarnings("rawtypes")
    private void writeNetworkAttributesHelper(final String namespace,
                                              final CyNetwork my_network,
                                              final List<AspectElement> elements) {

        final CyRow row = my_network.getRow(my_network, namespace);

        if (row != null) {
            final Map<String, Object> values = row.getAllValues();

            if ((values != null) && !values.isEmpty()) {
                for (final String column_name : values.keySet()) {
                    if (isIgnore(column_name, ADDITIONAL_IGNORE_FOR_NETWORK_ATTRIBUTES, Settings.INSTANCE)) {
                        continue;
                    }
                    final Object value = values.get(column_name);
                    if (value == null) {
                        continue;
                    }
                    NetworkAttributesElement e = null;
                    
                    final Long subnet;
                    if(my_network instanceof CyRootNetwork) {
                    		subnet = null;
                    } else {
                    		subnet = my_network.getSUID();
                    }
                    
                    if (value instanceof List) {
                        final List<String> attr_values = new ArrayList<String>();
                        for (final Object v : (List) value) {
                            attr_values.add(String.valueOf(v));
                        }
                        if (!attr_values.isEmpty()) {
                            e = new NetworkAttributesElement(subnet,
                                                             column_name,
                                                             attr_values,
                                                             AttributesAspectUtils.determineDataType(value));
                        }
                    }
                    else {
                        e = new NetworkAttributesElement(subnet,
                                                         column_name,
                                                         String.valueOf(value),
                                                         AttributesAspectUtils.determineDataType(value));
                    }
                    if (e != null) {
                        elements.add(e);
                    }
                }
            }
        }
    }

    private final void writeNodeAttributes(final CyNetwork network,
                                           final boolean write_siblings,
                                           final CxWriter w,
                                           final String namespace) throws IOException {

        final List<AspectElement> elements = new ArrayList<AspectElement>();

        final CySubNetwork my_subnet = (CySubNetwork) network;
        final CyRootNetwork my_root = my_subnet.getRootNetwork();
        final List<CySubNetwork> subnets = makeSubNetworkList(write_siblings, my_subnet, my_root, true);

        for (final CySubNetwork subnet : subnets) {
            writeNodeAttributesHelper(namespace, subnet, subnet.getNodeList(), elements);
        }

        final long t0 = System.currentTimeMillis();
        w.writeAspectElements(elements);
        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "node attributes", elements.size());
        }
    }


    private final void writeTableColumnLabels(final CyNetwork network,
    		final boolean write_siblings,
    		final CxWriter w) throws IOException {

    	final List<AspectElement> elements = new ArrayList<AspectElement>();

    	final CySubNetwork my_subnet = (CySubNetwork) network;
    	final CyRootNetwork my_root = my_subnet.getRootNetwork();
    	final List<CySubNetwork> subnets = makeSubNetworkList(write_siblings, my_subnet, my_root, true);

    	for (final CySubNetwork subnet : subnets) {
    		Collection<CyColumn> c = subnet.getDefaultNodeTable().getColumns();
    		for (CyColumn col : c) {
    			if ( ! col.getName().equals(CxUtil.SUID)) {
    				ATTRIBUTE_DATA_TYPE type = ATTRIBUTE_DATA_TYPE.STRING;
    				if (col.getType() != List.class ) {
    					type = toAttributeType(col.getType());
    				}
    				else {
    					type = toListAttributeType(col.getListElementType());
    				}

    				CyTableColumnElement x = new CyTableColumnElement(subnet.getSUID(), "node_table", col.getName(),type);
    				elements.add(x);
    			}
    		}
    	}
    	for (final CySubNetwork subnet : subnets) {
    		Collection<CyColumn> c = subnet.getDefaultEdgeTable().getColumns();
    		for (CyColumn col : c) {
    			if ( ! col.getName().equals(CxUtil.SUID)) {
    				ATTRIBUTE_DATA_TYPE type = ATTRIBUTE_DATA_TYPE.STRING;
    				if (col.getType() != List.class ) {
    					type = toAttributeType(col.getType());
    				}
    				else {
    					type = toListAttributeType(col.getListElementType());
    				}

    				CyTableColumnElement x = new CyTableColumnElement(subnet.getSUID(), "edge_table", col.getName(),type);
    				elements.add(x);
    			}
    		}
    	}
    	for (final CySubNetwork subnet : subnets) {
    		Collection<CyColumn> c = subnet.getDefaultNetworkTable().getColumns();
    		for (CyColumn col : c) {
    			if ( ! col.getName().equals(CxUtil.SUID)) {
    				ATTRIBUTE_DATA_TYPE type = ATTRIBUTE_DATA_TYPE.STRING;
    				if (col.getType() != List.class ) {
    					type = toAttributeType(col.getType());
    				}
    				else {
    					type = toListAttributeType(col.getListElementType());
    				}
    				CyTableColumnElement x = new CyTableColumnElement(subnet.getSUID(), "network_table", col.getName(),type);
    				elements.add(x);
    			}
    		}
    	}

    	final long t0 = System.currentTimeMillis();
    	w.writeAspectElements(elements);
    	if (Settings.INSTANCE.isTiming()) {
    		TimingUtil.reportTimeDifference(t0, "table columns ", elements.size());
    	}
    }
    

    private final static ATTRIBUTE_DATA_TYPE toAttributeType(final Class<?> attr_class )
            throws IOException {
        if (attr_class == String.class) {
            return ATTRIBUTE_DATA_TYPE.STRING;
        }
        else if ((attr_class == Float.class) || (attr_class == Double.class)) {
        	 return ATTRIBUTE_DATA_TYPE.DOUBLE;
        }
        else if ((attr_class == Integer.class) || (attr_class == Short.class)) {
        	 return ATTRIBUTE_DATA_TYPE.INTEGER;
        }
        else if (attr_class == Long.class) {
        	 return ATTRIBUTE_DATA_TYPE.LONG;
        }
        else if (attr_class == Boolean.class) {
        	 return ATTRIBUTE_DATA_TYPE.BOOLEAN;
        }
        else {
            throw new IllegalArgumentException("don't know how to deal with type '" + attr_class + "'");
        }
    }
    
    private final static ATTRIBUTE_DATA_TYPE toListAttributeType(final Class<?> attr_class )
            throws IOException {
        if (attr_class == String.class) {
            return ATTRIBUTE_DATA_TYPE.LIST_OF_STRING;
        }
        else if ((attr_class == Float.class) || (attr_class == Double.class)) {
            return ATTRIBUTE_DATA_TYPE.LIST_OF_DOUBLE;
        }
        else if ((attr_class == Integer.class) || (attr_class == Short.class)) {
            return ATTRIBUTE_DATA_TYPE.LIST_OF_INTEGER;
        }
        else if (attr_class == Long.class) {
            return ATTRIBUTE_DATA_TYPE.LIST_OF_LONG;
        }
        else if (attr_class == Boolean.class) {
            return ATTRIBUTE_DATA_TYPE.LIST_OF_BOOLEAN;
        }
        else {
            throw new IllegalArgumentException("don't know how to deal with type '" + attr_class + "'");
        }
    }
    
    
    @SuppressWarnings("rawtypes")
    private void writeNodeAttributesHelper(final String namespace,
                                           final CySubNetwork my_network,
                                           final List<CyNode> nodes,
                                           final List<AspectElement> elements) {
        for (final CyNode cy_node : nodes) {

            final CyRow row = my_network.getRow(cy_node, namespace);
            if (row != null) {
                final Map<String, Object> values = row.getAllValues();

                if ((values != null) && !values.isEmpty()) {
                    for (final String column_name : values.keySet()) {
                        if (isIgnore(column_name, ADDITIONAL_IGNORE_FOR_NODE_ATTRIBUTES, Settings.INSTANCE)) {
                            continue;
                        }
                        final Object value = values.get(column_name);
                        if (value == null) {
                            continue;
                        }
                        NodeAttributesElement e = null;
                        final long subnet = my_network.getSUID();
                        if (value instanceof List) {
                            final List<String> attr_values = new ArrayList<String>();
                            for (final Object v : (List) value) {
                                attr_values.add(String.valueOf(v));
                            }
                            if (!attr_values.isEmpty()) {
                                e = new NodeAttributesElement(subnet,
                                                              cy_node.getSUID(),
                                                              column_name,
                                                              attr_values,
                                                              AttributesAspectUtils.determineDataType(value));
                            }
                        }
                        else {
                            e = new NodeAttributesElement(subnet,
                                                          cy_node.getSUID(),
                                                          column_name,
                                                          String.valueOf(value),
                                                          AttributesAspectUtils.determineDataType(value));
                        }
                        if (e != null) {
                            elements.add(e);
                        }
                    }
                }
            }
        }
    }

    private final void writeSubNetworks(final CyNetwork network,
                                        final boolean write_siblings,
                                        final CxWriter w,
                                        final AspectSet aspects) throws IOException {

        final CySubNetwork my_subnet = (CySubNetwork) network;
        final CyRootNetwork my_root = my_subnet.getRootNetwork();
        final List<CySubNetwork> subnets = makeSubNetworkList(write_siblings, my_subnet, my_root, true);

        for (final CySubNetwork subnet : subnets) {
            final Collection<CyNetworkView> views = _networkview_manager.getNetworkViews(subnet);
            for (final CyNetworkView view : views) {

                if (aspects.contains(Aspect.CARTESIAN_LAYOUT)) {
                    writeCartesianLayout(view, w);
                }

                if (aspects.contains(Aspect.VISUAL_PROPERTIES)) {
                    writeVisualProperties(view, _visual_mapping_manager, _lexicon, w);
                }
            }
        }
        final List<AspectElement> elements = new ArrayList<AspectElement>();
        for (final CySubNetwork subnet : subnets) {
            final SubNetworkElement subnetwork_element = new SubNetworkElement(subnet.getSUID());
            for (final CyEdge edgeview : subnet.getEdgeList()) {
                subnetwork_element.addEdge(edgeview.getSUID());
            }
            for (final CyNode nodeview : subnet.getNodeList()) {
                subnetwork_element.addNode(nodeview.getSUID());
            }
            elements.add(subnetwork_element);
        }
        final long t0 = System.currentTimeMillis();
        w.writeAspectElements(elements);
        if (Settings.INSTANCE.isTiming()) {
            TimingUtil.reportTimeDifference(t0, "subnetworks", elements.size());
        }
    }

    private CxExporter() {
        _use_default_pretty_printing = DEFAULT_USE_DEFAULT_PRETTY_PRINTING;
        _write_pre_metdata = false;
        _write_post_metdata = false;
        _visual_mapping_manager = null;
        _next_suid = 0;
    }

}
