package org.cytoscape.io.internal.cxio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.AbstractAttributesAspectElement;
import org.ndexbio.cxio.aspects.datamodels.AttributesAspectUtils;
import org.ndexbio.cxio.aspects.datamodels.CartesianLayoutElement;
import org.ndexbio.cxio.aspects.datamodels.CyGroupsElement;
import org.ndexbio.cxio.aspects.datamodels.CyTableColumnElement;
import org.ndexbio.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgesElement;
import org.ndexbio.cxio.aspects.datamodels.HiddenAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NetworkRelationsElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.aspects.datamodels.SubNetworkElement;
import org.ndexbio.cxio.core.CxWriter;
import org.ndexbio.cxio.core.OpaqueAspectIterator;
import org.ndexbio.cxio.core.interfaces.AspectElement;
import org.ndexbio.cxio.core.interfaces.AspectFragmentWriter;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;
import org.ndexbio.cxio.misc.AspectElementCounts;
import org.ndexbio.cxio.misc.OpaqueElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.group.CyGroup;
import org.cytoscape.group.CyGroupManager;
import org.cytoscape.io.internal.AspectSet;
import org.cytoscape.io.internal.CyServiceModule;
import org.cytoscape.io.internal.cx_writer.VisualPropertiesGatherer;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.session.CySessionManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;

/**
 * This class is for serializing Cytoscape networks, views, and attribute tables
 * as CX formatted output streams. <br>
 * <br>
 * <br>
 * <br>
 * These methods use: <br>
 * <ul>
 * <li>{@link AspectSet} to control which aspects to serialize</li>
 * <li>aspect</li>
 * </ul>
 * <br>
 *
 * @see AspectSet
 * @see Aspect
 * @see CxOutput
 * @see CxImporter
 *
 *
 */
public final class CxExporter {
	Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private final boolean writeSiblings;
	private final boolean useCxId;
	private final CyNetwork baseNetwork;
	private final List<CySubNetwork> subnetworks;
	
	private boolean omitOpaqueAspects = false;
	private List<String> nodeColumns, edgeColumns, networkColumns;
	
	private HashMap<String, Long> idCounters = new HashMap<String, Long>();
	
	private Set<CyGroup> collapsed_groups;
	
	//Services needed to export
	private final CyGroupManager group_manager;
	private final CyNetworkViewManager _networkview_manager;
	
	private CxWriter writer;
	private String ID_STRING = "_id";

	/**
	 * Constructor for CxExporter to write network (and it's collection) to CX. Specify 
	 * if the exporter should attempt to use CX IDs from a previous import
	 * 
	 * @param network
	 * @param writeSiblings
	 * @param useCxId
	 */
	
	
	public CxExporter(CyNetwork network, boolean writeSiblings, boolean useCxId) {
		if (writeSiblings && useCxId) {
			throw new IllegalArgumentException("Cannot export a collection with CX IDs.");
		}
		this.writeSiblings = writeSiblings;
		this.useCxId = useCxId;
		
		subnetworks = makeSubNetworkList((CySubNetwork) network);
		if (subnetworks.isEmpty()) {
			throw new IllegalArgumentException("Could not find subnetworks to export");
		}
		this.baseNetwork = writeSiblings ? subnetworks.get(0).getRootNetwork() : network;
		
		group_manager = CyServiceModule.getService(CyGroupManager.class);
		_networkview_manager = CyServiceModule.getService(CyNetworkViewManager.class);
	}

	/**
	 * This is a method for serializing a Cytoscape network and associated table
	 * data as CX formatted OutputStream. <br>
	 * Method arguments control which aspects to serialize, and for data stored in
	 * node and tables (serialized as node attributes and edge attributes aspects),
	 * which table columns to include or exclude.
	 *
	 *
	 * @param aspects
	 *            the set of aspects to serialize
	 * @param out
	 *            the stream to write to
	 * @throws IOException
	 *
	 *
	 * @see AspectSet
	 * @see Aspect
	 * @see FilterSet
	 *
	 */

	public final void writeNetwork(Collection<String> aspects, final OutputStream out) throws IOException {
		
		if (aspects == null || aspects.isEmpty()) {
			aspects = AspectSet.getAspectNames();
		}
		
		// if specific aspects are specified, do not write opaque aspects
		if (aspects.size() != AspectSet.getAspectNames().size()) {
			omitOpaqueAspects = true;
		}
		
		String net_type = writeSiblings ? "collection" : "subnetwork";
		String id_type = useCxId ? "CX IDs" : "SUIDs";
		
		logger.info("Exporting network as " + net_type + " with " + id_type);
		logger.info("Aspect filter: " + aspects);
		logger.info("NodeCol filter: " + nodeColumns);
		logger.info("EdgeCol filter: " + edgeColumns);
		logger.info("NetworkCol filter: " + networkColumns);
		
		
		// Build session info that will be exported with network
		CySessionManager session_manager = CyServiceModule.getService(CySessionManager.class);
		session_manager.getCurrentSession();
		
		if (!aspects.contains(SubNetworkElement.ASPECT_NAME)) {
			if (aspects.contains(CyVisualPropertiesElement.ASPECT_NAME)) {
				throw new IllegalArgumentException("need to write sub-networks in order to write visual properties");
			}
			if (aspects.contains(CartesianLayoutElement.ASPECT_NAME)) {
				throw new IllegalArgumentException("need to write sub-networks in order to write cartesian layout");
			}
		}

		writer = CxWriter.createInstance(out, false);
		
		for (final AspectFragmentWriter aspect_writer : AspectSet.getAspectFragmentWriters(aspects)) {
			writer.addAspectFragmentWriter(aspect_writer);
		}

		MetaDataCollection meta_data = writePreMetaData(aspects);

		writer.start();

		String msg = null;
		boolean success = true;
		
		// Must expand all groups beforehand to reveal nodes
		collapsed_groups = expandGroups();
		
		try {
			
			// Write network table
			writeTableColumns();
			writeNetworkAttributes();
			
			// Write nodes, edges, and their attributes
			writeNodes(); // Handles CyGroups and internal nodes/edges
			writeEdges();
			writeNodeAttributes();
			writeEdgeAttributes();

			// Collection specific aspects
			if (writeSiblings) {
				writeCxIds();
			}
			// Writes Cartesian layout and visual props, only writes subnets for collections
			writeSubNetworks();
			
			// Also handles Opaque aspects
			writeHiddenAttributes(); 

			final AspectElementCounts aspects_counts = writer.getAspectElementCounts();

			writePostMetadata(meta_data, aspects_counts);
			CxUtil.setMetaData(baseNetwork, meta_data);

		} catch (final Exception e) {
			e.printStackTrace();
			msg = "Failed to create cx network: " + e.getMessage();
			success = false;
		} finally {
			collapsed_groups.forEach(group -> {
				for (CyNetwork net : group.getNetworkSet()) {
					if (net instanceof CySubNetwork) {
						group.collapse(net);
					}
				}
			});
		}
		

		writer.end(success, msg);

		if (success) {
			final AspectElementCounts counts = writer.getAspectElementCounts();
			if (counts != null) {
				System.out.println("Aspects elements written out:");
				System.out.println(counts);
			}

		}
	}

	// MetaData
	private MetaDataCollection writePreMetaData(Collection<String> aspects) {

		MetaDataCollection pre_meta_data = CxUtil.getMetaData(baseNetwork);
		if (pre_meta_data.isEmpty()) {
			for (AspectFragmentWriter aspect : AspectSet.getAspectFragmentWriters(aspects)) {
				if (aspect.getAspectName().equals(EdgesElement.ASPECT_NAME)) {
					if (baseNetwork.getEdgeCount() > 0) {
						addDataToMetaDataCollection(pre_meta_data, aspect.getAspectName(), null, null);
					}
				} else if (aspect.getAspectName().equals(EdgeAttributesElement.ASPECT_NAME)) {
					if (baseNetwork.getEdgeCount() > 0) {
						addDataToMetaDataCollection(pre_meta_data, aspect.getAspectName(), null, null);
					}
				} else {
					addDataToMetaDataCollection(pre_meta_data, aspect.getAspectName(), null, null);
				}
			}
		}
		writer.addPreMetaData(pre_meta_data);
		return pre_meta_data;
	}
	private final void writePostMetadata(final MetaDataCollection meta_data,
			final AspectElementCounts aspects_counts) {

		if (meta_data == null) {
			throw new IllegalArgumentException("Cannot populate null post metaData");
		}
		for (String name : aspects_counts.getAllAspectNames()) {
			long count = (long) aspects_counts.getAspectElementCount(name);			
			Long idCounter = idCounters.getOrDefault(name, null);
			if (count > 0) {
				addDataToMetaDataCollection(meta_data, name, count, idCounter);
			}
		}

		final long t0 = System.currentTimeMillis();
		writer.addPostMetaData(meta_data);
		if (Settings.INSTANCE.isTiming()) {
			TimingUtil.reportTimeDifference(t0, "post meta-data", -1);
		}
	}

	
	/**
	 * Write Cytoscape table column headers. Refer to writeTableColumnHelper for more
	 * @throws IOException
	 */
	private final void writeTableColumns() throws IOException {

		final List<AspectElement> elements = new ArrayList<>();
		
		if (writeSiblings) {
			addTableColumnsHelper(baseNetwork, "network_table", elements, CyRootNetwork.SHARED_ATTRS);
			addTableColumnsHelper(baseNetwork, "node_table", elements, CyRootNetwork.SHARED_ATTRS);
			addTableColumnsHelper(baseNetwork, "edge_table", elements, CyRootNetwork.SHARED_ATTRS);
		}
		
		for (final CySubNetwork subnet : subnetworks) {
			addTableColumnsHelper(subnet, "node_table", elements, CyNetwork.DEFAULT_ATTRS);
			addTableColumnsHelper(subnet, "edge_table", elements, CyNetwork.DEFAULT_ATTRS);
			addTableColumnsHelper(subnet, "network_table", elements, CyNetwork.DEFAULT_ATTRS);
		}

		writeAspectElements(elements);
	}
	/**
	 * Write table column info to CX. When writeSiblings, do not repeat shared columns for subnetworks
	 * @param network
	 * @param applies_to
	 * @param elements
	 * @param namespace
	 */
	private final void addTableColumnsHelper(CyNetwork network, String applies_to, List<AspectElement> elements, String namespace) {
		CyTable table = null;
		Set<String> additional_ignore;
		switch (applies_to) {
		case "node_table":
			table = network.getTable(CyNode.class, namespace);
			additional_ignore = Settings.IGNORE_NODE_ATTRIBUTES;
			break;
		case "edge_table":
			table = network.getTable(CyEdge.class, namespace);
			additional_ignore = Settings.IGNORE_EDGE_ATTRIBUTES;
			break;
		case "network_table":
			table = network.getTable(CyNetwork.class, namespace);
			additional_ignore = Settings.IGNORE_NETWORK_ATTRIBUTES;
			break;
		default:
			throw new IllegalArgumentException("Unknown applies_to in CyTableColumn: " + applies_to);
		}
		Collection<CyColumn> c = table.getColumns();
		
		for (CyColumn col : c) {
			
			if (Settings.isIgnore(col.getName(), additional_ignore, null)){
				continue;
			}
			if (col.getName().startsWith(CxUtil.OPAQUE_ASPECT_PREFIX)) {
				continue;
			}
			ATTRIBUTE_DATA_TYPE type = ATTRIBUTE_DATA_TYPE.STRING;
			if (col.getType() != List.class) {
				type = CxUtil.toAttributeType(col.getType());
			} else {
				type = CxUtil.toListAttributeType(col.getListElementType());
			}

			Long subnetId = getAspectSubnetworkId(network);
			
			// when writing a collection, skip shared columns in subnetworks. They are added by the root tables 
			if (writeSiblings && (network instanceof CySubNetwork && col.getVirtualColumnInfo().isVirtual())) {
				continue;
			}
			CyTableColumnElement x = new CyTableColumnElement(subnetId, applies_to, col.getName(), type);
			
			elements.add(x);
		}
	}
	
	
	// Network Attributes
	private final void writeNetworkAttributes() throws IOException {

		final List<AspectElement> elements = new ArrayList<>();
		
		// Write root table
		if (writeSiblings) {
			addNetworkAttributesHelper(CyRootNetwork.DEFAULT_ATTRS, baseNetwork, elements);
		}
		
		for (final CySubNetwork subnet : subnetworks) {
			addNetworkAttributesHelper(CyNetwork.DEFAULT_ATTRS, subnet, elements);
		}

		writeAspectElements(elements);
	}
	
	private final void writeHiddenAttributes() throws IOException {

		final List<AspectElement> elements = new ArrayList<>();
		if (writeSiblings) {
			addNetworkAttributesHelper(CyNetwork.HIDDEN_ATTRS, baseNetwork, elements);
		}
		for (final CySubNetwork subnet : subnetworks) {
			addNetworkAttributesHelper(CyNetwork.HIDDEN_ATTRS, subnet, elements);
		}

		writeAspectElements(elements);
	}

	private void writeOpaqueElement(String column, String value)
			throws JsonParseException, IOException {
		if (omitOpaqueAspects) {
			return;
		}
		InputStream in = new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
		OpaqueAspectIterator iter = new OpaqueAspectIterator(in);
		
		writer.startAspectFragment(column);
		while (iter.hasNext()) {
			OpaqueElement el = iter.next();
			JsonNode node = el.getData().get("@id");
			if (node != null) {
				Long max = Long.max(node.asLong(), idCounters.getOrDefault(column, 0l));
				idCounters.put(column, max);
			}
			writer.writeOpaqueAspectElement(el);
		}
		writer.endAspectFragment();
	}
	
	// Nodes, edges
	private final void writeNodes() throws IOException {
		// Handles nodes (and edges/nodes inside group nodes)
		final HashMap<String, List<AspectElement>> elementMap = new HashMap<String, List<AspectElement>>();
		
		elementMap.put(NodesElement.ASPECT_NAME, new ArrayList<AspectElement>());
		elementMap.put(CyGroupsElement.ASPECT_NAME, new ArrayList<AspectElement>());

		for (final CyNode cy_node : baseNetwork.getNodeList()) {
			addNodesAndGroupsElements(elementMap, cy_node, baseNetwork);
		}

		// Iterate through nodes (and groups/edges/attributes) aspects
		for (String aspect : elementMap.keySet()) {
			writeAspectElements(elementMap.get(aspect));
		}
	}
	
	private final void writeEdges() throws IOException {

		final List<AspectElement> edgeElements = new ArrayList<>();
		
		for (CyEdge edge : baseNetwork.getEdgeList()) {
			edgeElements.add(createEdgeElement(edge, baseNetwork));
		}
		writeAspectElements(edgeElements);
	}

	private void writeNodeAttributes() throws IOException {
		List<AspectElement> nodeAttributes = new ArrayList<AspectElement>();
		
		List<String> shared_cols = new ArrayList<String>();
		//Write shared attributes first
		if (writeSiblings) {
			CyTable table = baseNetwork.getTable(CyNode.class, CyRootNetwork.SHARED_ATTRS);
			table.getColumns().forEach((col) -> {
				shared_cols.add(col.getName());
			});
			
			for (CyNode node : baseNetwork.getNodeList()) {
				CyRow row = baseNetwork.getRow(node, CyRootNetwork.SHARED_ATTRS);
				row.getAllValues().forEach((name, value) -> {
					addNodeAttributesElement(nodeAttributes, baseNetwork, node, name, value);
				});
			}
		}
		
		
		for (CySubNetwork network : subnetworks) {
			for (CyNode node : network.getNodeList()) {
				CyRow row = network.getRow(node, CyNetwork.DEFAULT_ATTRS);
				row.getAllValues().forEach((name, value) -> {
					if (!shared_cols.contains(name)) {
						addNodeAttributesElement(nodeAttributes, network, node, name, value);
					}
				});
			}
		}
		writeAspectElements(nodeAttributes);
	}
		
	private void writeEdgeAttributes() throws IOException{
		List<AspectElement> edgeAttributes = new ArrayList<AspectElement>();
		
		final List<String> shared_cols = new ArrayList<String>();
		//Write shared attributes first
		if (writeSiblings) {
			CyTable table = baseNetwork.getTable(CyEdge.class, CyRootNetwork.SHARED_ATTRS);
			table.getColumns().forEach(col -> {
				shared_cols.add(col.getName());
			});
			
			for (CyEdge edge : baseNetwork.getEdgeList()) {
				CyRow row = baseNetwork.getRow(edge, CyRootNetwork.SHARED_ATTRS);
				row.getAllValues().forEach((name, value) -> {
					addEdgeAttributesElement(edgeAttributes, baseNetwork, edge, name, value);
				});
			}
		}
		
		
		for (CySubNetwork network : subnetworks) {
			for (CyEdge edge : network.getEdgeList()) {
				CyRow row = network.getRow(edge);//, CyNetwork.DEFAULT_ATTRS);
				row.getAllValues().entrySet().stream()
					.filter(entry -> !shared_cols.contains(entry.getKey()))
					.forEach(e -> {
						addEdgeAttributesElement(edgeAttributes, network, edge, e.getKey(), e.getValue());
						}
					);
			}
		}
		writeAspectElements(edgeAttributes);
	}

	
	// Aggregators
	private void addDataToMetaDataCollection(final MetaDataCollection meta_data, final String aspect_name,
			final Long count, final Long id_counter) {

		if (count != null && count == 0) {
			return;
		}
		MetaDataElement e = meta_data.getMetaDataElement(aspect_name);
		if (e == null) {
			e = new MetaDataElement(aspect_name, "1.0");
			meta_data.add(e);
		}
		if (count != null) {
			e.setElementCount(count);
		}
		if (id_counter != null) {
			e.setIdCounter(id_counter);
		}
	}
	/**
	 * Add network attriubtes for a network to a collection to be written. This method is different from 
	 * addNode and addEdge in that even shared (virtual) columns must be written because they are not
	 * consistent across collections.
	 *  
	 * @param namespace
	 * @param my_network
	 * @param elements
	 * @throws IOException 
	 * @throws JsonParseException 
	 */
	@SuppressWarnings("rawtypes")
	private void addNetworkAttributesHelper(final String namespace, final CyNetwork my_network,
			final List<AspectElement> elements) throws JsonParseException, IOException {

		final CyRow row = my_network.getRow(my_network, namespace);
		
		if (row == null) {
			return;
		}
				
		final Map<String, Object> values = row.getAllValues();
		if (values == null) {
			return;
		}
		for (final String column_name : values.keySet()) {
			System.out.println("writing column: " + column_name + " from " + my_network.toString());
			
			final Object value = values.get(column_name);
			if (value == null) {
				continue;
			}
			// Ignore columns like SUID, etc
			if (Settings.isIgnore(column_name, Settings.IGNORE_NETWORK_ATTRIBUTES, value)) {
				continue;
			}
			
			if (networkColumns != null && !networkColumns.contains(column_name)) {
				continue;
			}
			
			if (column_name.startsWith(CxUtil.OPAQUE_ASPECT_PREFIX)) {
				writeOpaqueElement(column_name.substring(CxUtil.OPAQUE_ASPECT_PREFIX.length()), (String) value);
				continue;
			}

			// Only include subnet SUID if writing collection
			Long subnet = getAspectSubnetworkId(my_network);
			AbstractAttributesAspectElement element = null;
			ATTRIBUTE_DATA_TYPE type = AttributesAspectUtils.determineDataType(value);
			if (value instanceof List) {
				final List<String> attr_values = new ArrayList<>();
				for (final Object v : (List) value) {
					attr_values.add(String.valueOf(v));
				}
				if (!attr_values.isEmpty()) {
					if (namespace.equals(CyNetwork.HIDDEN_ATTRS)) {
						element = new HiddenAttributesElement(subnet, column_name, attr_values, type);
					}else {
						element = new NetworkAttributesElement(subnet, column_name, attr_values, type);
					}
				}
			} else {
				if (namespace.equals(CyNetwork.HIDDEN_ATTRS)) {
					element = new HiddenAttributesElement(subnet, column_name, String.valueOf(value), type);
				}else {
					element = new NetworkAttributesElement(subnet, column_name, String.valueOf(value), type);
				}
			}
			if (element != null) {
				elements.add(element);
			}
		}
	}
	
	private void addNetworkRelationsElements(final List<AspectElement> elements, CySubNetwork subnetwork) throws IOException {
		String name = CxUtil.getNetworkName(subnetwork);
		// Subnetworks does not need root ID since it's not used in CX.
		elements.add(new NetworkRelationsElement(null, subnetwork.getSUID(),
				NetworkRelationsElement.TYPE_SUBNETWORK, name));
		
		
		// PLEASE NOTE: Cytoscape UI currently has only one view per sub-network.
		final Collection<CyNetworkView> views = _networkview_manager.getNetworkViews(subnetwork);
		int i = 0;
		for (final CyNetworkView view : views) {
			String title = view.getVisualProperty(BasicVisualLexicon.NETWORK_TITLE);
			if (title == null || title.isEmpty()) {
				title = name + " view";
				if (views.size() > 1) {
					title += " " + ++i;
				}
			}
			Long viewId = getViewId(view);
			elements.add(new NetworkRelationsElement(subnetwork.getSUID(),
					viewId, NetworkRelationsElement.TYPE_VIEW, title));
			
		}
	}
	private void addNodesAndGroupsElements(final Map<String, List<AspectElement>> elementMap, CyNode node,
			CyNetwork network) throws JsonProcessingException {
		List<AspectElement> nodes = elementMap.get(NodesElement.ASPECT_NAME);

		if (group_manager.isGroup(node, network)) {
			Long cxId = CxUtil.getElementId(node, network, useCxId);
			nodes.add(new NodesElement(cxId, null, null));
			
			List<AspectElement> groups = elementMap.get(CyGroupsElement.ASPECT_NAME);
			CyGroup group = group_manager.getGroup(node, network);
			addGroupElement(groups, network, group);
		} else {
			nodes.add(createNodeElement(node, network));
		}

	}
	
	private void addGroupElement(List<AspectElement> elements, CyNetwork network, CyGroup group) throws JsonProcessingException {
		String name = null;
		final CyRow row = network.getRow(group.getGroupNode());
		if (row != null) {
			name = row.get(CyNetwork.NAME, String.class);
		}
		if (name == null) {
			name = row.get(CyRootNetwork.SHARED_NAME, String.class);
		}
		
		boolean isCollapsed = collapsed_groups.contains(group);
		
		Long subnetId = getAspectSubnetworkId(network);
		final CyGroupsElement group_element = 
				new CyGroupsElement(
					CxUtil.getElementId(group.getGroupNode(), network, useCxId),
					subnetId, name);
		
		group.getExternalEdgeList().forEach(edge -> {
			group_element.addExternalEdge(CxUtil.getElementId(edge, network, useCxId));
		});
		group.getInternalEdgeList().forEach(e -> {
			group_element.addInternalEdge(CxUtil.getElementId(e, network, useCxId));
		});
		group.getNodeList().forEach(n -> {
			group_element.addNode(CxUtil.getElementId(n, network, useCxId));
		});

		group_element.set_isCollapsed(isCollapsed);
		elements.add(group_element);
	}

	private void addNodeAttributesElement(final List<AspectElement> elements, CyNetwork network, CyNode node, String name, Object value) {
		if (value == null) {
			return;
		}

		if (Settings.isIgnore(name, Settings.IGNORE_NODE_ATTRIBUTES, value)) {
			return;
		}
		if (nodeColumns != null && !nodeColumns.contains(name)) {
			return;
		}
		
		Long nodeId = CxUtil.getElementId(node, network, useCxId);
		Long subnetworkId = getAspectSubnetworkId(network);
		ATTRIBUTE_DATA_TYPE type = AttributesAspectUtils.determineDataType(value);
		
		if (!type.isSingleValueType()) {
			final List<String> attr_values = new ArrayList<>();
			for (final Object v : (List<?>) value) {
				attr_values.add(String.valueOf(v));
			}
			if (!attr_values.isEmpty()) {
				elements.add(new NodeAttributesElement(subnetworkId, nodeId, name, attr_values, type));
			}
			
		}else {
			elements.add(new NodeAttributesElement(subnetworkId, nodeId, name, String.valueOf(value), type));
		}
	}

	

	private void addEdgeAttributesElement(final List<AspectElement> elements, CyNetwork network, CyEdge edge, String name, Object value) {
		
		if (value == null || (value instanceof String && ((String) value).length() == 0)) {
			return;
		}

		if (Settings.isIgnore(name, Settings.IGNORE_EDGE_ATTRIBUTES, value)) {
			return;
		}
		
		if (edgeColumns != null && !edgeColumns.contains(name)) {
			return;
		}
		Long edgeId = CxUtil.getElementId(edge, network, useCxId);
		Long subnetworkId = getAspectSubnetworkId(network);
		if (value instanceof List) {
			final List<String> attr_values = new ArrayList<>();
			for (final Object v : (List<?>) value) {
				attr_values.add(String.valueOf(v));
			}
			if (!attr_values.isEmpty()) {
				elements.add(new EdgeAttributesElement(subnetworkId, edgeId, name, attr_values,
						AttributesAspectUtils.determineDataType(value)));
			}
		} else {
			elements.add(new EdgeAttributesElement(subnetworkId, edgeId, name, String.valueOf(value),
					AttributesAspectUtils.determineDataType(value)));
		}
	}
	
	// Collection Opaques
	/**
	 * Write CX IDs to opaque aspect in CX. Only necessary for collection export to maintain IDs on import
	 * @throws IOException
	 */
	private void writeCxIds() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode data = mapper.createObjectNode();
		
		CyRootNetwork root = ((CySubNetwork) subnetworks.get(0)).getRootNetwork();
		if (!CxUtil.hasCxIds(root)){
			return;
		}
		for (CySubNetwork net : subnetworks) {
			for (CyNode node : net.getNodeList()) {
				String suid = String.valueOf(node.getSUID());
				Long cxId = CxUtil.getCxId(node, root);
				if (cxId != null) {
					data.put(suid, cxId);
				}
			}
			for (CyEdge edge : net.getEdgeList()) {
				String suid = String.valueOf(edge.getSUID());
				Long cxId = CxUtil.getCxId(edge, root);
				if (cxId != null) {
					data.put(suid, cxId);
				}
			}
		}
		if (data.size() > 0) {
			OpaqueElement element = new OpaqueElement(CxUtil.CX_ID_MAPPING, data);
			writer.startAspectFragment(CxUtil.CX_ID_MAPPING);
			writer.writeOpaqueAspectElement(element);
			writer.endAspectFragment();
		}

	}

	/**
	 * Iterates through all subnetworks in the list (singleton or singleton and siblings) and
	 * collects the following aspects:
	 * - CySubNetwork
	 * - NetworkRelations
	 * - CartesianLayout
	 * - CyVisualProperties
	 * @param aspects
	 * @throws IOException
	 */
	private final void writeSubNetworks() throws IOException {
		
		final List<AspectElement> cySubnetworkElements = new ArrayList<>();
		final List<AspectElement> networkRelationsElements = new ArrayList<>();
		
		// write the visual properties and coordinates
		for (final CySubNetwork subnet : subnetworks) {
			
			final Collection<CyNetworkView> views = _networkview_manager.getNetworkViews(subnet);
			
			// Use node/edge in views, instead of from subnetwork to avoid hidden/group elements
			HashSet<Long> edge_suids = new HashSet<Long>();
			HashSet<Long> node_suids = new HashSet<Long>();
			
			for (final CyNetworkView view : views) {
				final VisualLexicon _lexicon = CxUtil.getLexicon(view);
				writeCartesianLayout(view);
				writeVisualProperties(view, _lexicon);
				
				view.getEdgeViews().forEach(ev -> {
					edge_suids.add(ev.getModel().getSUID());
				});
				view.getNodeViews().forEach(nv -> {
					node_suids.add(nv.getModel().getSUID());
				});
			}
			
			// Required for all networks, subnets can have multiple views
			if (writeSiblings || views.size() > 1) {
				addNetworkRelationsElements(networkRelationsElements, subnet);
			}
			if (writeSiblings) {
				final SubNetworkElement subnetwork_element = new SubNetworkElement(subnet.getSUID());
				subnetwork_element.setEdges(new ArrayList<Long>(edge_suids));
				subnetwork_element.setNodes(new ArrayList<Long>(node_suids));

				cySubnetworkElements.add(subnetwork_element);
			}
		}
		
		if (!cySubnetworkElements.isEmpty()) {
			writeAspectElements(cySubnetworkElements);
		}
		if (!networkRelationsElements.isEmpty()) {
			writeAspectElements(networkRelationsElements);
		}

	}
	
	// Views
	private final void writeCartesianLayout(final CyNetworkView view) throws IOException {

		final CyNetwork network = view.getModel();
		final List<AspectElement> elements = new ArrayList<>(network.getNodeCount());

		
		boolean z_used = false;
		for (View<CyNode> node_view : view.getNodeViews()) {
			Double z = node_view.getVisualProperty(BasicVisualLexicon.NODE_Z_LOCATION);
			if (z != null && Math.abs(z) > 0.000000001) {
				z_used = true;
				break;
			}
		}

		Long viewId = getViewId(view);
		for (View<CyNode> node_view : view.getNodeViews()) {
			Long nodeId = CxUtil.getElementId(node_view.getModel(), network, useCxId);
			if (z_used) {
				elements.add(new CartesianLayoutElement(nodeId, viewId,
						node_view.getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION),
						node_view.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION),
						node_view.getVisualProperty(BasicVisualLexicon.NODE_Z_LOCATION)));
			} else {
				Double x = node_view.getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
				Double y = node_view.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION);

				elements.add(new CartesianLayoutElement(nodeId, viewId, x.toString(), y.toString()));
			}
		}

		writeAspectElements(elements);
	}

	private final void writeVisualProperties(final CyNetworkView view, final VisualLexicon lexicon) throws IOException {

		final Set<VisualPropertyType> types = new HashSet<>();
		types.add(VisualPropertyType.NETWORK);
		types.add(VisualPropertyType.NODES);
		types.add(VisualPropertyType.EDGES);
		types.add(VisualPropertyType.NODES_DEFAULT);
		types.add(VisualPropertyType.EDGES_DEFAULT);

		final Long viewId = getViewId(view);
		
		final List<AspectElement> elements = VisualPropertiesGatherer.gatherVisualPropertiesAsAspectElements(view, lexicon, types, viewId, useCxId);
		writeAspectElements(elements);
	}

	
	//Creators
	private EdgesElement createEdgeElement(CyEdge edge, CyNetwork network) throws JsonProcessingException {
		Long cxId = CxUtil.getElementId(edge, network, useCxId);
		Long sourceId = CxUtil.getElementId(edge.getSource(), network, useCxId);
		Long targetId = CxUtil.getElementId(edge.getTarget(), network, useCxId);
		
		String interaction = null;
		if (writeSiblings) {
			CyRow row = network.getRow(edge, CyNetwork.DEFAULT_ATTRS);
			interaction = row.get(CyRootNetwork.SHARED_INTERACTION, String.class);
		}else{
			interaction = network.getRow(edge).get(CyEdge.INTERACTION, String.class);
		}
		
		EdgesElement element = new EdgesElement(cxId, sourceId, targetId, interaction);
		return element;
	}
	private NodesElement createNodeElement(CyNode node, CyNetwork network) throws JsonProcessingException {
		Long cxId = CxUtil.getElementId(node, network, useCxId);
		String attName = writeSiblings ? CyRootNetwork.SHARED_NAME : CyNetwork.NAME;
		String name = getNodeAttributeValue(network, node, attName, String.class);
		String repr = getNodeAttributeValue(network, node, CxUtil.REPRESENTS, String.class);
		return new NodesElement(cxId, name, repr);
	}
	
	// Utility Functions
	/**
	 * Write a list of aspect elements to CX and output the time it took
	 * @param elements
	 * @throws IOException
	 */
	private void writeAspectElements(List<AspectElement> elements) throws IOException {
		if (elements == null || elements.isEmpty()) {
			return;
		}
		Gson gson = new Gson();
		for (AspectElement el : elements) {
			JsonElement json_el = gson.toJsonTree(el);
			JsonObject obj = json_el.getAsJsonObject();
			
			if (!obj.has(ID_STRING)) {
				break;
			}
			Long id = obj.get(ID_STRING).getAsLong();
			
			Long max = Math.max(id, idCounters.getOrDefault(el.getAspectName(), 0l));
			idCounters.put(el.getAspectName(), max);
		}
		final long t0 = System.currentTimeMillis();
		writer.writeAspectElements(elements);
		if (Settings.INSTANCE.isTiming()) {
			TimingUtil.reportTimeDifference(t0, elements.get(0).getAspectName(), elements.size());
		}
	}
	/**
	 * Return a list of the subnetworks used to build the CX document. Can be only 
	 * the subnetwork, or all subnetworks if writeSiblings is true
	 * @param subnet
	 * @return
	 */
	private final List<CySubNetwork> makeSubNetworkList(CySubNetwork subnet) {
		List<CySubNetwork> subnets = new ArrayList<>();
		
		if (writeSiblings) {
			CyRootNetwork root = subnet.getRootNetwork();
			for (final CySubNetwork s : root.getSubNetworkList()) {
				String name = CxUtil.getNetworkName(s);
				// CyGroups are created with null-named networks. DO NOT write these
				if (name == null) {
					continue;
				}
				subnets.add(s);
			}
		} else {
			subnets = new ArrayList<>();
			subnets.add(subnet);
		}
		return subnets;
	}
	
	/**
	 * Return the network SUID for CX Aspects. Is null for singletons or collection aspects
	 * @param subnet
	 * @return
	 */
	private Long getAspectSubnetworkId(CyNetwork net) {
		if (writeSiblings && !(net instanceof CyRootNetwork)) {
			return net.getSUID();
		}
		return null;
	}
	
	/**
	 * Return the network SUID for CX Aspects. Is null for singletons or collection aspects
	 * @param subnet
	 * @return
	 */
	private Long getViewId(CyNetworkView view) {
		CyNetwork network = view.getModel();
		CyNetworkViewManager view_manager = CyServiceModule.getService(CyNetworkViewManager.class);
		if (writeSiblings || view_manager.getNetworkViews(network).size() > 1) {
			return view.getSUID();
		}
		return null;
	}
	
	// Static Helpers
	private Set<CyGroup> expandGroups() {
		Set<CyGroup> groups = new HashSet<CyGroup>();
		for (CySubNetwork net : subnetworks) {
			group_manager.getGroupSet(net).forEach(group -> {
				if (group.isCollapsed(net)) {
					groups.add(group);
					group.expand(net);
				}
			});
		}
		group_manager.getGroupSet(baseNetwork).forEach(group -> {
			if (group.isCollapsed(baseNetwork)) {
				groups.add(group);
				group.expand(baseNetwork);
			}
		});
		
		return groups;
	}

	@SuppressWarnings("unchecked")
	private static final <T> T getNodeAttributeValue(final CyNetwork network, final CyNode node, String colName,
			Class<? extends T> type) {
		CyTable table = network.getTable(CyNode.class, CyNetwork.DEFAULT_ATTRS);
		final CyColumn col = table.getColumn(colName);
		
		final CyRow row = table.getRow(node.getSUID());
		if (col != null && row != null) {
			if (col.getType() == List.class) {
				return (T) row.getList(colName, type);
			}
			return row.get(colName, type);
		}
		return null;
	}
	
	public void setNodeColumnFilter(List<String> selectedValues) {
		if (selectedValues != null && !selectedValues.isEmpty()) {
			this.nodeColumns = selectedValues;
		}
	}
	public void setEdgeColumnFilter(List<String> selectedValues) {
		if (selectedValues != null && !selectedValues.isEmpty()) {
			this.edgeColumns = selectedValues;
		}
	}
	public void setNetworkColumnFilter(List<String> selectedValues) {
		if (selectedValues != null && !selectedValues.isEmpty()) {
			this.networkColumns = selectedValues;
		}
	}
	
}

