package org.cytoscape.io.internal.cxio;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.commons.lang3.ArrayUtils;
import org.cytoscape.event.CyEventHelper;
import org.cytoscape.io.internal.AspectSet;
import org.cytoscape.io.internal.CxPreferences;
import org.cytoscape.io.internal.CyServiceModule;
import org.cytoscape.io.internal.cx_reader.ViewMaker;
import org.cytoscape.io.internal.nicecy.NiceCyNetwork;
import org.cytoscape.io.internal.nicecy.NiceCyRootNetwork;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.BooleanVisualProperty;
import org.cytoscape.view.presentation.property.DoubleVisualProperty;
import org.cytoscape.view.presentation.property.IntegerVisualProperty;
import org.cytoscape.view.presentation.property.ObjectPositionVisualProperty;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualPropertyDependency;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.VisualStyleFactory;
import org.ndexbio.cx2.aspect.element.core.CxAspectElement;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.CxEdgeBypass;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.CxNodeBypass;
import org.ndexbio.cx2.aspect.element.core.CxOpaqueAspectElement;
import org.ndexbio.cx2.aspect.element.core.CxVisualProperty;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cx2.aspect.element.core.VisualPropertyMapping;
import org.ndexbio.cx2.aspect.element.core.VisualPropertyTable;
import org.ndexbio.cx2.aspect.element.cytoscape.VisualEditorProperties;
import org.ndexbio.cx2.converter.CX2ToCXVisualPropertyConverter;
import org.ndexbio.cx2.io.CXReader;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.CartesianLayoutElement;
import org.ndexbio.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgesElement;
import org.ndexbio.cxio.aspects.datamodels.Mapping;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.aspects.datamodels.SubNetworkElement;
import org.ndexbio.cxio.core.CxElementReader2;
import org.ndexbio.cxio.core.interfaces.AspectElement;
import org.ndexbio.cxio.core.interfaces.AspectFragmentReader;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;
import org.ndexbio.cxio.misc.OpaqueElement;
import org.ndexbio.model.cx.NdexNetworkStatus;
import org.ndexbio.model.cx.NiceCXNetwork;
import org.ndexbio.model.exceptions.NdexException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This class is for de-serializing CX2 formatted network. 
 *
 *
 *
 */
public final class Cx2Importer {
	
	private static final Logger logger = LoggerFactory.getLogger("CX2 Importer");

    private InputStream input;
	
    private CyNetwork base;
    
    private CyRootNetwork root;
    
    private CxAttributeDeclaration attrDecls;
    
    private boolean createView;
    
    private CyNetworkView currentView;
    
    private CyTable nodeTable;
    private CyTable edgeTable;
    private CyTable networkTable;
    
    //CX ID to suid mapping table
    private Map<Long,Long> nodeIdMap;
    
    //CX ID to suid mapping table
    private Map<Long,Long> edgeIdMap;
    
    // node suid to CxNodes mapping table. 
    private Map<Long, CxNode> cxNodes;
    
    private CxVisualProperty visualProperties;
    
    private List<CxNodeBypass> nodeBypasses;
    private List<CxEdgeBypass> edgeBypasses;
    
	private Map<String, Collection<CxOpaqueAspectElement>> opaqueAspects;
	
	private String name;
	
	private VisualEditorProperties editorProperties;
	
	private boolean hasLayout;

    public Cx2Importer(InputStream in, boolean createView) {

    	this.input = in;
    	this.createView = createView;
    	nodeIdMap = new TreeMap<>();
    	edgeIdMap = new TreeMap<>();
    	cxNodes = new TreeMap<>();
    	hasLayout = false;
    	
    	base = null;
    	currentView = null;
    	visualProperties = null;
    	nodeBypasses = new LinkedList<>();
    	edgeBypasses = new LinkedList<>();
    	opaqueAspects = new HashMap<>();
    	name = null;
    	editorProperties = null;
    }


  
    public CyNetwork importNetwork() throws IOException, NdexException {
        long t0 = System.currentTimeMillis();
		
        long nodeIdCounter = 0;
        long edgeIdCounter = 0;
        
		CXReader cxreader = new CXReader(input);
		
		CyNetworkFactory network_factory = CyServiceModule.getService(CyNetworkFactory.class);
		
		base = network_factory.createNetwork();
		root = ((CySubNetwork)base).getRootNetwork();
		
		  
		for ( CxAspectElement elmt : cxreader ) {
			switch ( elmt.getAspectName() ) {
				case CxAttributeDeclaration.ASPECT_NAME:
					attrDecls = (CxAttributeDeclaration)elmt;
					if ( !attrDecls.getDeclarations().isEmpty())
						initializeTables();
					break;
				case CxNode.ASPECT_NAME :       //Node
					createNode((CxNode) elmt);
					break;
				case CxEdge.ASPECT_NAME:       // Edge
					CxEdge ee = (CxEdge) elmt;
					createEdge(ee);
					break;
				case CxNetworkAttribute.ASPECT_NAME: //network attributes
					createNetworkAttribute(( CxNetworkAttribute) elmt);
					break;
				case CxVisualProperty.ASPECT_NAME: 
					visualProperties = (CxVisualProperty) elmt;
					break;
				case CxNodeBypass.ASPECT_NAME: 
					nodeBypasses.add((CxNodeBypass) elmt );
					break;
				case CxEdgeBypass.ASPECT_NAME:
					edgeBypasses.add((CxEdgeBypass) elmt);
					break;
				case VisualEditorProperties.ASPECT_NAME: 
					if ( this.editorProperties == null) 
						this.editorProperties = (VisualEditorProperties) elmt;
					else 
						throw new NdexException("Only one " + VisualEditorProperties.ASPECT_NAME + " element is allowed in CX2.");
					break;
				default:    // opaque aspect
					addOpaqueAspectElement((CxOpaqueAspectElement)elmt);
			}

		} 
   
		serializeOpaqueAspects();
		
     	// create the view
	/*	CyEventHelper cyEventHelper = CyServiceModule.getService(CyEventHelper.class);
		cyEventHelper.flushPayloadEvents();
	*/
		return base;
    }
    
    private void initializeTables() {
        	
    	if (attrDecls.getDeclarations().isEmpty())
    		return;
    	
    	networkTable = root.getTable(CyNetwork.class, CyRootNetwork.DEFAULT_ATTRS); 
		createTableAttrs(attrDecls.getDeclarations().get(CxNetworkAttribute.ASPECT_NAME),networkTable);
		
		nodeTable = root.getTable(CyNode.class, CyNetwork.DEFAULT_ATTRS);
		createTableAttrs(attrDecls.getDeclarations().get(CxNode.ASPECT_NAME),nodeTable);

		edgeTable = root.getTable(CyEdge.class, CyNetwork.DEFAULT_ATTRS);
		createTableAttrs(attrDecls.getDeclarations().get(CxEdge.ASPECT_NAME),edgeTable);
		
    }

    private static void createTableAttrs(Map<String, DeclarationEntry> attrsDecls, CyTable table) {
    	if ( attrsDecls!=null && !attrsDecls.isEmpty()) {
    		for ( Map.Entry<String, DeclarationEntry> e: attrsDecls.entrySet()) {
    			ATTRIBUTE_DATA_TYPE dtype= e.getValue().getDataType();
    			if (dtype == null)
    				dtype = ATTRIBUTE_DATA_TYPE.STRING;
    			CxUtil.createColumn(table, e.getKey(), CxUtil.getDataType( dtype), dtype.isSingleValueType());
    		}
    	}	
    }
    
    
    private void createNode(CxNode node) throws NdexException {
    	
    	if (!hasLayout) {
    		if ( node.getX()!=null)
    			hasLayout = true;
    	}
    	
    	Map<String,DeclarationEntry> attributeDeclarations = attrDecls.getAttributesInAspect(CxNode.ASPECT_NAME);
    	node.extendToFullNode(attributeDeclarations);
		node.validateAttribute(attributeDeclarations, true);
    	// add node to cy data model.
    	Long nodesuid = this.nodeIdMap.get(node.getId());
    	if ( nodesuid == null) {
    		CyNode cyNode = createCyNodeByCXId(node.getId());
    		nodesuid = cyNode.getSUID();		
    	} 

    	// add attributes
		final CyRow localRow = nodeTable.getRow(nodesuid);

		for ( Map.Entry<String,Object> e: node.getAttributes().entrySet()) {
			if (nodeTable.getColumn(e.getKey()) != null) {
				localRow.set(e.getKey(), e.getValue());
			} else 
				throw new NdexException("Node attribute " + e.getKey() + " is not declared.");
		}
    	
		// add cxnode to table
		cxNodes.put(nodesuid, node);
		
		/*if ( currentView !=null) {
            final View<CyNode> node_view = currentView.getNodeView(cyNode);

		}*/
    }
    
    
    private CyNode createCyNodeByCXId(Long cxNodeId) { 
		CyNode cyNode = base.addNode();		
		CxUtil.saveCxId(cyNode, base, cxNodeId);
		nodeIdMap.put(cxNodeId, cyNode.getSUID());
		return cyNode;
    }
    
    private void createEdge(CxEdge edge) throws NdexException {

    	Map<String,DeclarationEntry> attributeDeclarations = attrDecls.getAttributesInAspect(CxEdge.ASPECT_NAME);
    	edge.extendToFullNode(attributeDeclarations);
		edge.validateAttribute(attributeDeclarations, true);
 	    	
    	// add edge 
    	CyNode src,tgt;
    	
    	Long srcsuid = this.nodeIdMap.get(edge.getSource());
    	if ( srcsuid == null) {
    		src = createCyNodeByCXId(edge.getSource());
    	} else 
    		src = root.getNode(srcsuid);
    	
    	Long tgtsuid = this.nodeIdMap.get(edge.getTarget());
    	if ( tgtsuid == null) {
    		tgt = createCyNodeByCXId ( edge.getTarget());
    	} else
    		tgt = root.getNode(tgtsuid);
    	
		CyEdge cyEdge = base.addEdge(src, tgt, true);
		CxUtil.saveCxId(cyEdge, base, edge.getId() );
    	this.edgeIdMap.put(edge.getId(), cyEdge.getSUID());
    	
    	// edge edge attributes
		final CyRow localRow = edgeTable.getRow(cyEdge.getSUID());

		for ( Map.Entry<String,Object> e: edge.getAttributes().entrySet()) {
			if (edgeTable.getColumn(e.getKey()) != null) {
				localRow.set(e.getKey(), e.getValue());
			} else 
				throw new NdexException("Edge attribute " + e.getKey() + " is not declared.");
		}
		
    }
    
    private void createNetworkAttribute(CxNetworkAttribute netAttrs) throws NdexException {
		final CyRow sharedRow = networkTable.getRow(root.getSUID());
		
		netAttrs.extendToFullNode(this.attrDecls.getAttributesInAspect(CxNetworkAttribute.ASPECT_NAME));

		for ( Map.Entry<String,Object> e: netAttrs.getAttributes().entrySet()) {
			if (edgeTable.getColumn(e.getKey()) != null) {
				sharedRow.set(e.getKey(), e.getValue());
				if ( e.getKey().equals(CxNetworkAttribute.nameAttribute))
					this.name = (String)e.getValue();
			} else 
				throw new NdexException("Network attribute " + e.getKey() + " is not declared.");
		}
    }
    
    private void addOpaqueAspectElement(CxOpaqueAspectElement e) {
    	Collection<CxOpaqueAspectElement> aspect = this.opaqueAspects.get(e.getAspectName());
    	if ( aspect == null)
    		aspect = new ArrayList<>();
    	aspect.add(e);
    }
    
	private void serializeOpaqueAspects() {
		ObjectMapper mapper = new ObjectMapper();

		opaqueAspects.forEach((name, opaque) -> {
			if (ArrayUtils.contains(NiceCyRootNetwork.UNSERIALIZED_OPAQUE_ASPECTS, name)) {
				// Do not serialize some opaque aspects
				return;
			}
				String column = CxUtil.OPAQUE_ASPECT_PREFIX + name;
				CyTable table = base.getTable(CyNetwork.class, CyRootNetwork.SHARED_ATTRS);

				String aspectStr;
				try {
					aspectStr = mapper.writeValueAsString(opaque);
					CxUtil.createColumn(table, name, String.class, true);
					table.getRow(base.getSUID()).set(column, aspectStr);
				} catch (JsonProcessingException e) {	
					//TODO: log warning messages.
					e.printStackTrace();
				}

			
		});
	}

	public String getNetworkName() {
		return name;
	}
    
	public CyNetworkView createView() throws NdexException {
		if ( createView) {
			CyNetworkViewFactory view_factory = CyServiceModule.getService(CyNetworkViewFactory.class);
			CyNetworkViewManager view_manager = CyServiceModule.getService(CyNetworkViewManager.class);

			currentView = view_factory.createNetworkView(base);		
			view_manager.addNetworkView(currentView);
			
			currentView.setVisualProperty(BasicVisualLexicon.NETWORK_TITLE, name);
			makeView();

		}
			
		// add table styles
		
		return currentView;
	}
	
	
	private void makeView() throws NdexException {
		final VisualMappingManager visual_mapping_manager = CyServiceModule.getService(VisualMappingManager.class);
    	final VisualStyleFactory visual_style_factory = CyServiceModule.getService(VisualStyleFactory.class);
    	final RenderingEngineManager rendering_engine_manager = CyServiceModule.getService(RenderingEngineManager.class);
	
    	String doLayout = currentView.getEdgeViews().size() < CxPreferences.getLargeLayoutThreshold() ? "force-directed" : "grid";

        final boolean have_default_visual_properties = 
        		(visualProperties != null) ||
                (!nodeBypasses.isEmpty()) || 
                (!edgeBypasses.isEmpty());
        
        VisualStyle new_visual_style = visual_mapping_manager.getDefaultVisualStyle();
        if (have_default_visual_properties) {
            int counter = 1;
            final VisualStyle default_visual_style = visual_mapping_manager.getDefaultVisualStyle();
            new_visual_style = visual_style_factory.createVisualStyle(default_visual_style);
            
            final String viz_style_title_base = ViewMaker.createTitleForNewVisualStyle(name);
            
            String viz_style_title = viz_style_title_base;
            while (counter < 101) {
                if (ViewMaker.containsVisualStyle(viz_style_title, visual_mapping_manager)) {
                    viz_style_title = viz_style_title_base + "-" + counter;
                }
                counter++;
            }
            //ViewMaker.removeVisualStyle(viz_style_title, visual_mapping_manager);
            new_visual_style.setTitle(viz_style_title);
        }

        
        if(hasLayout) {
        	for ( Map.Entry<Long, CxNode> e: cxNodes.entrySet()) {
        		CyNode node = base.getNode(e.getKey());
                final View<CyNode> nodeView = currentView.getNodeView(node);
                if (nodeView != null) {
                	CxNode n = e.getValue();
                    nodeView.setVisualProperty(BasicVisualLexicon.NODE_X_LOCATION, n.getX());
                    nodeView.setVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION, n.getY());
                    if (n.getZ() !=null) {
                        nodeView.setVisualProperty(BasicVisualLexicon.NODE_Z_LOCATION,
                                                    n.getZ());
                    }
                }
        	}
        	doLayout = null;
        }
        
        if ( visualProperties != null) {
        	VisualLexicon lexicon = rendering_engine_manager.getDefaultVisualLexicon();

        	setNetworkVPs(lexicon,visualProperties.getDefaultProps().getNetworkProperties(),new_visual_style);
        
        	setNodeVPs (lexicon, visualProperties.getDefaultProps().getNodeProperties(), new_visual_style);
        	
			if (editorProperties != null) {
				for (Map.Entry<String, Object> e : editorProperties.getProperties().entrySet()) {
					String vpName = e.getKey();
					if (vpName.startsWith("NETWORK_")) {
						final VisualProperty vp = lexicon.lookup(CyNetwork.class, vpName);
						if (vp != null) {
							Object cyVPValue  = getCyVPValueFromCX2VPValue(vp, e.getValue());	
							if ( cyVPValue != null) {
								new_visual_style.setDefaultValue(vp, cyVPValue);
							}
						}
					} else {  //set the dependencies
				    	for (final VisualPropertyDependency<?> d : new_visual_style.getAllVisualPropertyDependencies()) {
				            if (d.getIdString().equals(vpName)) {
				                try {
				                    d.setDependency((Boolean)e.getValue());
				                }
				                catch (final Exception ex) {
				                    throw new NdexException("could not parse boolean from '" + vpName + "'");
				                }
				            }
				            break;
				        }

					}
					
				}

			}

        }
        
        ViewMaker.applyStyle(new_visual_style,currentView,doLayout, false);
        
        
	}
	
	private void setNetworkVPs(final VisualLexicon lexicon,
			 Map<String,Object> defaults, VisualStyle style) {
		if (defaults != null) {
			for (final Map.Entry<String, Object> entry : defaults.entrySet()) {
				String cyVPName = CX2ToCXVisualPropertyConverter.getInstance().getCx1NetworkPropertyName(entry.getKey());
				if ( cyVPName != null) {
					final VisualProperty vp = lexicon.lookup(CyNetwork.class, cyVPName);
					if (vp != null) {
						Object cyVPValue  = getCyVPValueFromCX2VPValue(vp, entry.getValue());	
						if ( cyVPValue != null) {
							style.setDefaultValue(vp, cyVPValue);
						}
					}
				}
			}
		}
		
	}

	private void setNodeVPs(final VisualLexicon lexicon,
			VisualPropertyTable defaults, VisualStyle style) {
		if (defaults != null) {
			//preprocess NODE_SIZE 
			if (editorProperties !=null && editorProperties.getProperties().get("nodeSizeLocked")!=null &&
					editorProperties.getProperties().get("nodeSizeLocked").equals(Boolean.TRUE)) {
				VisualProperty<Double> vp = BasicVisualLexicon.NODE_SIZE;
				Object v = defaults.get("NODE_WIDTH");
				if ( v!=null) {
					Object cyVPValue  = getCyVPValueFromCX2VPValue(vp, v);
					style.setDefaultValue(vp, (Double)cyVPValue);
				}
			}
			
			for (final Map.Entry<String, Object> entry : defaults.getVisualProperties().entrySet()) {
				String cyVPName = CX2ToCXVisualPropertyConverter.getInstance().getCx1EdgeOrNodeProperty(entry.getKey());
				if ( cyVPName != null) {
					VisualProperty vp = lexicon.lookup(CyNode.class, cyVPName);
					if (vp != null) {
						Object cyVPValue  = getCyVPValueFromCX2VPValue(vp, entry.getValue());	
						if ( cyVPValue != null) {
							style.setDefaultValue(vp, cyVPValue);
						}
					}
				}
			}
		}
		
	}
	
	
	private void setDefaultVisualPropertiesAndMappings(final VisualLexicon lexicon,
			 VisualPropertyTable defaults, Map<String, VisualPropertyMapping> mappings,
			 VisualStyle style, final Class my_class) {

		if (defaults != null) {
			for (final Map.Entry<String, Object> entry : defaults.getVisualProperties().entrySet()) {
				final VisualProperty vp = lexicon.lookup(my_class, entry.getKey());
				if (vp != null) {
					Object cyVPValue  = getCyVPValueFromCX2VPValue(vp, entry.getValue());	
				    if ( cyVPValue != null) {
				    	style.setDefaultValue(vp, cyVPValue);
				    }
				}
			}
		}


/*		if (maps != null) {
			for (final Entry<String, Mapping> entry : maps.entrySet()) {
				try {
					parseVisualMapping(entry.getKey(), entry.getValue(), lexicon, style, my_class);
				} catch (IOException e) {
					logger.warn("Failed to parse visual mapping: " + e);
				}

			}
		} */

	/*	if (dependencies != null) {
			for (final Entry<String, String> entry : dependencies.entrySet()) {
				try {
					parseVisualDependency(entry.getKey(), entry.getValue(), style);
				} catch (IOException e) {
					logger.warn("Failed to parse visual dependency: " + e);
				}
			}
		} */
	}

	
	public static <T> T getCyVPValueFromCX2VPValue(VisualProperty<T> vp, Object cx2Value) {
		if  (vp instanceof ObjectPositionVisualProperty )
			return null;
		if ( vp.getIdString().startsWith("NODE_CUSTOMGRAPHICS_SIZE_", 0))
			return null;
	  	if ( cx2Value instanceof String)
	  		return vp.parseSerializableString((String)cx2Value);
		if(vp instanceof DoubleVisualProperty)
			return  (T)Double.valueOf(  ((Number)cx2Value).doubleValue()); 
		if ( vp instanceof BooleanVisualProperty)
	  		return (T)cx2Value;
	  	if (vp instanceof IntegerVisualProperty)
	  		return  (T)Integer.valueOf(  ((Number)cx2Value).intValue());
	  	
	  	return null;
	}
	
}
