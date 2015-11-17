package org.cytoscape.io.internal.cx_reader;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cxio.aspects.datamodels.CartesianLayoutElement;
import org.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.cytoscape.io.internal.cxio.CxUtil;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.View;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.VisualStyleFactory;
import org.cytoscape.view.vizmap.mappings.BoundaryRangeValues;
import org.cytoscape.view.vizmap.mappings.ContinuousMapping;
import org.cytoscape.view.vizmap.mappings.DiscreteMapping;
import org.cytoscape.view.vizmap.mappings.PassthroughMapping;

public final class ViewMaker {

    public static final Pattern  DIRECT_NET_PROPS_PATTERN = Pattern
                                                                  .compile("GRAPH_VIEW_(ZOOM|CENTER_(X|Y))|NETWORK_(WIDTH|HEIGHT|SCALE_FACTOR|CENTER_(X|Y|Z)_LOCATION)");

    private static final boolean DEBUG                    = true;

    public final static CyNetworkView makeView(final CyNetwork network,
                                               final CxToCy cx_to_cy,
                                               final String network_collection_name,
                                               final CyNetworkViewFactory networkview_factory,
                                               final RenderingEngineManager rendering_engine_manager,
                                               final VisualMappingManager visual_mapping_manager,
                                               final VisualStyleFactory visual_style_factory,
                                               final VisualMappingFunctionFactory vmf_factory_c,
                                               final VisualMappingFunctionFactory vmf_factory_d,
                                               final VisualMappingFunctionFactory vmf_factory_p) {
        final CyNetworkView view = networkview_factory.createNetworkView(network);
        final VisualElementCollectionMap collection = cx_to_cy.getVisualElementCollectionMap();
        if (collection != null) {

            final Long network_id = cx_to_cy.getNetworkSuidToNetworkRelationsMap().get(network.getSUID());

            if (DEBUG) {
                System.out.println("network_id=" + network_id);
            }
            // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            // PLEASE NOTE
            // -----------
            // For now, Cytoscape has only one view per
            // sub-network, but in the future this
            // might change, which means instead of "get(0)" we will need a
            // loop.
            // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            if (!cx_to_cy.getSubNetworkToViewsMap().containsKey(network_id)) {
                throw new IllegalStateException("no view for sub-network " + network_id + " found");
            }

            final Long subnetwork_id = cx_to_cy.getSubNetworkToViewsMap().get(network_id).get(0);
            if (DEBUG) {
                System.out.println("subnetwork_id=" + subnetwork_id);
            }

            final boolean have_default_visual_properties = ((collection
                    .getNetworkVisualPropertiesElement(subnetwork_id) != null)
                    || (collection.getNodesDefaultVisualPropertiesElement(subnetwork_id) != null) || (collection
                            .getEdgesDefaultVisualPropertiesElement(subnetwork_id) != null));

            VisualStyle new_visual_style = null;
            if (have_default_visual_properties) {
                int counter = 1;
                final VisualStyle default_visual_style = visual_mapping_manager.getDefaultVisualStyle();
                new_visual_style = visual_style_factory.createVisualStyle(default_visual_style);
                final String viz_style_title_base = ViewMaker.createTitleForNewVisualStyle(network_collection_name);
                String viz_style_title = viz_style_title_base;
                while (counter < 101) {
                    if (ViewMaker.containsVisualStyle(viz_style_title, visual_mapping_manager)) {
                        viz_style_title = viz_style_title_base + "-" + counter;
                    }
                    counter++;
                }
                ViewMaker.removeVisualStyle(viz_style_title, visual_mapping_manager);
                new_visual_style.setTitle(viz_style_title);
            }
            final VisualLexicon lexicon = rendering_engine_manager.getDefaultVisualLexicon();

            if (collection.getNetworkVisualPropertiesElement(subnetwork_id) != null) {
                ViewMaker
                        .setDefaultVisualPropertiesAndMappings(lexicon,
                                                               collection
                                                                       .getNetworkVisualPropertiesElement(subnetwork_id)
                                                                       .getProperties(),
                                                               new_visual_style,
                                                               CyNetwork.class,
                                                               vmf_factory_c,
                                                               vmf_factory_d,
                                                               vmf_factory_p);
            }
            else {
                if (DEBUG) {
                    System.out.println("no network default visual properties for sub-network " + subnetwork_id);
                }
            }

            if (collection.getNodesDefaultVisualPropertiesElement(subnetwork_id) != null) {
                ViewMaker
                        .setDefaultVisualPropertiesAndMappings(lexicon,
                                                               collection
                                                                       .getNodesDefaultVisualPropertiesElement(subnetwork_id)
                                                                       .getProperties(),
                                                               new_visual_style,
                                                               CyNode.class,
                                                               vmf_factory_c,
                                                               vmf_factory_d,
                                                               vmf_factory_p);
            }
            else {
                if (DEBUG) {
                    System.out.println("no nodes default visual properties for sub-network " + subnetwork_id);
                }
            }

            if (collection.getEdgesDefaultVisualPropertiesElement(subnetwork_id) != null) {
                ViewMaker
                        .setDefaultVisualPropertiesAndMappings(lexicon,
                                                               collection
                                                                       .getEdgesDefaultVisualPropertiesElement(subnetwork_id)
                                                                       .getProperties(),
                                                               new_visual_style,
                                                               CyEdge.class,
                                                               vmf_factory_c,
                                                               vmf_factory_d,
                                                               vmf_factory_p);
            }
            else {
                if (DEBUG) {
                    System.out.println("no edges default visual properties for sub-network " + subnetwork_id);
                }
            }

            ViewMaker.setNodeVisualProperties(view,
                                              lexicon,
                                              collection,
                                              subnetwork_id,
                                              cx_to_cy.getNodesWithVisualProperties());

            ViewMaker.setEdgeVisualProperties(view,
                                              lexicon,
                                              collection,
                                              subnetwork_id,
                                              cx_to_cy.getEdgesWithVisualProperties());

            final Map<CyNode, CartesianLayoutElement> position_map_for_view = collection
                    .getCartesianLayoutElements(subnetwork_id);

            if ((position_map_for_view != null) && (view != null)) {
                for (final CyNode node : position_map_for_view.keySet()) {
                    if (node != null) {
                        final CartesianLayoutElement e = position_map_for_view.get(node);
                        if (e != null) {
                            final View<CyNode> node_view = view.getNodeView(node);
                            if (node_view != null) {
                                node_view.setVisualProperty(BasicVisualLexicon.NODE_X_LOCATION,
                                                            Double.valueOf(e.getX()));
                                node_view.setVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION,
                                                            Double.valueOf(e.getY()));
                                if (e.isZset()) {
                                    node_view.setVisualProperty(BasicVisualLexicon.NODE_Z_LOCATION,
                                                                Double.valueOf(e.getZ()));
                                }
                            }
                        }
                    }
                }
            }
            else {
                if (DEBUG) {
                    System.out.println("no cartesian layout for sub-network " + subnetwork_id);
                }
            }

            if (have_default_visual_properties) {
                visual_mapping_manager.addVisualStyle(new_visual_style);
                new_visual_style.apply(view);
                visual_mapping_manager.setCurrentVisualStyle(new_visual_style);
                visual_mapping_manager.setVisualStyle(new_visual_style, view);
            }
        }
        return view;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public final static void addContinuousMapping(final VisualStyle style,
                                                  final VisualProperty vp,
                                                  final StringParser sp,
                                                  final String col,
                                                  final String type,
                                                  final Class<?> type_class,
                                                  final VisualMappingFunctionFactory vmf_factory_c) {
        final ContinuousMapping cmf = (ContinuousMapping) vmf_factory_c
                .createVisualMappingFunction(col, type_class, vp);

        if (cmf != null) {
            int counter = 0;
            while (true) {
                final String ov = sp.get("OV=" + counter);
                if (ov == null) {
                    break;
                }
                final String l = sp.get("L=" + counter);
                final String e = sp.get("E=" + counter);
                final String g = sp.get("G=" + counter);
                if ((l != null) && (e != null) && (g != null)) {
                    final Object lp = vp.parseSerializableString(l);
                    final Object ep = vp.parseSerializableString(e);
                    final Object gp = vp.parseSerializableString(g);
                    if ((lp != null) && (ep != null) && (gp != null)) {
                        final BoundaryRangeValues point = new BoundaryRangeValues(lp, ep, gp);
                        cmf.addPoint(ViewMaker.toTypeValue(ov, type), point);
                    }
                    else {
                        System.out.println("could not parse from string in continuous mapping for col '" + col + "'");
                    }
                }
                else {
                    System.out.println("could not get expected values in continuous mapping for col '" + col + "'");
                }
                counter++;
            }
            style.addVisualMappingFunction(cmf);
        }
        else {
            System.out.println("could not create continuous mapping for col '" + col + "'");
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public final static void addDiscreteMapping(final VisualStyle style,
                                                final VisualProperty vp,
                                                final StringParser sp,
                                                final String col,
                                                final String type,
                                                final Class<?> type_class,
                                                final VisualMappingFunctionFactory vmf_factory_d) {
        final DiscreteMapping dmf = (DiscreteMapping) vmf_factory_d.createVisualMappingFunction(col, type_class, vp);
        if (dmf != null) {
            int counter = 0;
            while (true) {
                final String k = sp.get("K=" + counter);
                if (k == null) {
                    break;
                }
                final String v = sp.get("V=" + counter);
                if (v != null) {
                    final Object pv = vp.parseSerializableString(v);
                    if (pv != null) {
                        dmf.putMapValue(ViewMaker.toTypeValue(k, type), pv);
                    }
                    else {
                        System.out.println("could not parse serializable string from discrete mapping value '" + v
                                           + "'");
                    }
                }
                else {
                    System.out.println("error: discrete mapping function string is corrupt");
                }
                counter++;
            }
            style.addVisualMappingFunction(dmf);
        }
        else {
            System.out.println("could not create discrete mapping for col '" + col + "'");
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public final static void addPasstroughMapping(final VisualStyle style,
                                                  final VisualProperty vp,
                                                  final String col,
                                                  final Class<?> type_class,
                                                  final VisualMappingFunctionFactory vmf_factory_p) {
        final PassthroughMapping pmf = (PassthroughMapping) vmf_factory_p.createVisualMappingFunction(col,
                                                                                                      type_class,
                                                                                                      vp);
        if (pmf != null) {
            style.addVisualMappingFunction(pmf);
        }
        else {
            System.out.println("could not create passthrough mapping for col '" + col + "'");
        }
    }

    public final static String createTitleForNewVisualStyle(final String network_collection_name) {
        String viz_style_title = "new-Style";
        if (network_collection_name != null) {
            if (network_collection_name.toLowerCase().endsWith(".cx")) {
                viz_style_title = String.format("%s-Style", network_collection_name.substring(0,
                                                                                              network_collection_name
                                                                                              .length() - 3));
            }
            else {
                viz_style_title = String.format("%s-Style", network_collection_name);
            }
        }
        return viz_style_title;
    }

    public final static void removeVisualStyle(final String viz_style_title,
                                               final VisualMappingManager visual_mapping_manager) {
        final Iterator<VisualStyle> it = visual_mapping_manager.getAllVisualStyles().iterator();
        while (it.hasNext()) {
            final VisualStyle vs = it.next();
            if (vs.getTitle().equalsIgnoreCase(viz_style_title)) {
                visual_mapping_manager.removeVisualStyle(vs);
                break;
            }
        }
    }

    public final static boolean containsVisualStyle(final String viz_style_title,
                                                    final VisualMappingManager visual_mapping_manager) {
        final Iterator<VisualStyle> it = visual_mapping_manager.getAllVisualStyles().iterator();
        while (it.hasNext()) {
            final VisualStyle vs = it.next();
            if (vs.getTitle().equalsIgnoreCase(viz_style_title)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public final static void setDefaultVisualPropertiesAndMappings(final VisualLexicon lexicon,
                                                                   final SortedMap<String, String> props,
                                                                   final VisualStyle style,
                                                                   final Class my_class,
                                                                   final VisualMappingFunctionFactory vmf_factory_c,
                                                                   final VisualMappingFunctionFactory vmf_factory_d,
                                                                   final VisualMappingFunctionFactory vmf_factory_p) {
        if (props != null) {

            for (final Map.Entry<String, String> entry : props.entrySet()) {
                final String key = entry.getKey();
                boolean is_mapping = false;
                char mapping = '?';
                String mapping_key = null;
                if (key.startsWith(CxUtil.PASSTHROUGH_MAPPING)) {
                    is_mapping = true;
                    mapping = 'p';
                    mapping_key = key.substring(20);
                }
                else if (key.startsWith(CxUtil.CONTINUOUS_MAPPING)) {
                    is_mapping = true;
                    mapping = 'c';
                    mapping_key = key.substring(19);
                }
                else if (key.startsWith(CxUtil.DISCRETE_MAPPING)) {
                    is_mapping = true;
                    mapping = 'd';
                    mapping_key = key.substring(17);
                }

                if (is_mapping) {
                    final VisualProperty vp = lexicon.lookup(my_class, mapping_key);
                    final StringParser sp = new StringParser(entry.getValue());
                    final String col = sp.get(CxUtil.VM_COL);
                    final String type = sp.get(CxUtil.VM_TYPE);
                    final Class<?> type_class = ViewMaker.toClass(type);
                    if (vp != null) {
                        if (mapping == 'p') {
                            addPasstroughMapping(style, vp, col, type_class, vmf_factory_p);
                        }
                        else if (mapping == 'c') {
                            addContinuousMapping(style, vp, sp, col, type, type_class, vmf_factory_c);
                        }
                        else if (mapping == 'd') {
                            addDiscreteMapping(style, vp, sp, col, type, type_class, vmf_factory_d);
                        }
                        else {
                            throw new IllegalStateException("unknown mapping type: " + mapping);
                        }
                    }
                }
                else {
                    final VisualProperty vp = lexicon.lookup(my_class, key);
                    if (vp != null) {
                        final Object parsed_value = vp.parseSerializableString(entry.getValue());
                        if (parsed_value != null) {
                            style.setDefaultValue(vp, parsed_value);
                        }
                        else {
                            System.out.println("could not parse serializable string from '" + entry.getValue() + "'");
                        }
                    }
                }
            }
        }
    }

    public final static void setEdgeVisualProperties(final CyNetworkView view,
                                                     final VisualLexicon lexicon,
                                                     final VisualElementCollectionMap collection,
                                                     final Long subnetwork_id,
                                                     final Collection<CyEdge> edges_with_visual_properties) {

        if (edges_with_visual_properties != null) {
            for (final CyEdge edge : edges_with_visual_properties) {
                final Map<CyEdge, CyVisualPropertiesElement> evpm = collection
                        .getEdgeVisualPropertiesElementsMap(subnetwork_id);
                final CyVisualPropertiesElement vpe = evpm.get(edge);

                if (vpe != null) {
                    final SortedMap<String, String> props = vpe.getProperties();
                    if (props != null) {
                        final View<CyEdge> v = view.getEdgeView(edge);
                        ViewMaker.setVisualProperties(lexicon, props, v, CyEdge.class);
                    }
                }
                else {
                    if (DEBUG) {
                        System.out.println("edges visual properties element for edge " + edge.getSUID()
                                           + " in sub-network " + subnetwork_id + " is null");
                    }
                }
            }
        }
    }

    public final static void setNodeVisualProperties(final CyNetworkView view,
                                                     final VisualLexicon lexicon,
                                                     final VisualElementCollectionMap collection,
                                                     final Long subnetwork_id,
                                                     final Collection<CyNode> nodes_with_visual_properties) {

        if (nodes_with_visual_properties != null) {
            for (final CyNode node : nodes_with_visual_properties) {
                final Map<CyNode, CyVisualPropertiesElement> nvpm = collection
                        .getNodeVisualPropertiesElementsMap(subnetwork_id);
                final CyVisualPropertiesElement vpe = nvpm.get(node);

                if (vpe != null) {
                    final SortedMap<String, String> props = vpe.getProperties();
                    if (props != null) {
                        final View<CyNode> v = view.getNodeView(node);
                        ViewMaker.setVisualProperties(lexicon, props, v, CyNode.class);
                    }
                }
                else {
                    if (DEBUG) {
                        System.out.println("nodes visual properties element for node " + node.getSUID()
                                           + " in sub-network " + subnetwork_id + " is null");
                    }
                }
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public final static void setVisualProperties(final VisualLexicon lexicon,
                                                 final SortedMap<String, String> props,
                                                 final View view,
                                                 final Class my_class) {
        if (props != null) {
            for (final Map.Entry<String, String> entry : props.entrySet()) {
                final VisualProperty vp = lexicon.lookup(my_class, entry.getKey());

                if (vp != null) {
                    final Object parsed_value = vp.parseSerializableString(entry.getValue());
                    if (parsed_value != null) {
                        if (ViewMaker.shouldSetAsLocked(vp)) {
                            view.setLockedValue(vp, parsed_value);
                        }
                        else {
                            view.setVisualProperty(vp, parsed_value);
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    public final static boolean shouldSetAsLocked(final VisualProperty vp) {
        if (vp.getTargetDataType() == CyNode.class) {
            if ((vp == BasicVisualLexicon.NODE_X_LOCATION) || (vp == BasicVisualLexicon.NODE_Y_LOCATION)
                    || (vp == BasicVisualLexicon.NODE_Z_LOCATION)) {
                return false;
            }
        }
        else if (vp.getTargetDataType() == CyNetwork.class) { // TODO //FIXME
            final Matcher netMatcher = DIRECT_NET_PROPS_PATTERN.matcher(vp.getIdString());
            return !netMatcher.matches();
        }
        return true;
    }

    public final static Class<?> toClass(final String type) {
        if (type.equals("string")) {
            return String.class;
        }
        else if (type.equals("integer")) {
            return Integer.class;
        }
        else if (type.equals("long")) {
            return Long.class;
        }
        else if (type.equals("double") || type.equals("float")) {
            return Double.class;
        }
        else if (type.equals("boolean")) {
            return Boolean.class;
        }
        else {
            throw new IllegalArgumentException("don't know how to deal with type '" + type + "'");
        }
    }

    public final static Object toTypeValue(final String s, final String type) {
        if (type.equals("string")) {
            return s;
        }
        else if (type.equals("integer")) {
            return Double.valueOf(s).intValue();
        }
        else if (type.equals("long")) {
            return Double.valueOf(s).longValue();
        }
        else if (type.equals("double") || type.equals("float")) {
            return Double.valueOf(s);
        }
        else if (type.equals("boolean")) {
            return Boolean.valueOf(s);
        }
        else {
            throw new IllegalArgumentException("don't know how to deal with type '" + type + "'");
        }
    }

}
