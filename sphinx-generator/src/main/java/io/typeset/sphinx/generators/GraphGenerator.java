package io.typeset.sphinx.generators;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.GraphPath;

import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.IntegerComponentNameProvider;
import org.jgrapht.io.StringComponentNameProvider;

import io.typeset.sphinx.exceptions.InvalidClauseException;
import io.typeset.sphinx.exceptions.InvalidLiteralException;
import io.typeset.sphinx.exceptions.InvalidModelException;
import io.typeset.sphinx.exceptions.InvalidKeyException;

import io.typeset.sphinx.model.App;
import io.typeset.sphinx.model.Control;
import io.typeset.sphinx.model.GraphNode;
import io.typeset.sphinx.model.Model;
import io.typeset.sphinx.model.NodeType;
import io.typeset.sphinx.model.Page;
import io.typeset.sphinx.model.Screen;
import io.typeset.sphinx.model.Widget;
import io.typeset.sphinx.model.assertions.Clause;
import io.typeset.sphinx.model.assertions.ExplicitAssertion;
import io.typeset.sphinx.model.assertions.Literal;
import io.typeset.sphinx.readers.ConfigReader;

/**
 * The Class GraphGenerator. Converts yml model into a graph.
 */
public class GraphGenerator {

	private static final Logger logger = LogManager.getLogger("GraphGenerator");

	/** The model of the product extracted from yml */
	private Model model;

	/** The map of yml tag names to node */
	private Map<String, GraphNode> nameNodeMap;

	/** The graph representing the model */
	private DefaultDirectedGraph<GraphNode, DefaultEdge> graph;

	/** The root node in the graph */
	private GraphNode rootNode;

	/** The target dir. */
	private String targetDir;

	/** The nodes with precondition. */
	private List<GraphNode> nodesWithPrecondition;

	/** Screen to page mapping */
	private Map<String, String> screenToPage;

	/**
	 * Instantiates a new graph generator.
	 *
	 * @param model
	 *            the model
	 * @param targetDir
	 *            the target dir
	 */
	public GraphGenerator(Model model) {
		this.model = model;
		this.targetDir = ConfigReader.outputDir;
	}

	/**
	 * Gets the node by yml key.
	 *
	 * @param key
	 *            the key
	 * @return the node by key
	 */
	public GraphNode getNodeByKey(String key) {
		GraphNode node = nameNodeMap.get(key);
		if (node != null) {
			return node;
		}
		logger.error("No such symbol as " + key);
		throw new InvalidKeyException("Mapping not found for key " + key);
	}

	/**
	 * Initialize the graph from the yml model
	 *
	 * @return the default directed graph
	 * @throws IllegalAccessException
	 *             the illegal access exception
	 * @throws InvocationTargetException
	 *             the invocation target exception
	 */
	public DefaultDirectedGraph<GraphNode, DefaultEdge> initialize()
			throws IllegalAccessException, InvocationTargetException {

		logger.debug("Initializing graph");

		if (model == null) {
			logger.error("model cannot be null");
			throw new InvalidModelException("model cannot be null");
		}
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);
		nameNodeMap = new HashMap<>();
		nodesWithPrecondition = new ArrayList<>();
		screenToPage = new HashMap<>();

		// add all the vertices
		logger.debug("Adding vertices");
		for (String c : model.getControls().keySet()) {
			logger.info("Adding control " + c);
			GraphNode v = createNewVertex(model.getControls().get(c), NodeType.CONTROL);
			graph.addVertex(v);
			addToMap(c, v);
		}
		for (String w : model.getWidgets().keySet()) {
			logger.info("Adding widget " + w);
			GraphNode v = createNewVertex(model.getWidgets().get(w), NodeType.WIDGET);
			graph.addVertex(v);
			nameNodeMap.put(w, v);
		}
		for (String a : model.getApps().keySet()) {
			logger.info("Adding app " + a);
			GraphNode v = createNewVertex(model.getApps().get(a), NodeType.APP);
			graph.addVertex(v);
			addToMap(a, v);
		}
		for (String s : model.getScreens().keySet()) {
			logger.info("Adding screen " + s);
			GraphNode v = createNewVertex(model.getScreens().get(s), NodeType.SCREEN);
			graph.addVertex(v);
			addToMap(s, v);
		}
		for (String p : model.getPages().keySet()) {
			logger.info("Adding page " + p);
			GraphNode v = createNewVertex(model.getPages().get(p), NodeType.PAGE);
			graph.addVertex(v);
			addToMap(p, v);
		}

		logger.info("number of vertices : " + graph.vertexSet().size());

		// add the edges

		List<GraphNode> controlsThatLeadToParent = new ArrayList<>();

		for (String c : model.getControls().keySet()) {

			GraphNode controlNode = getNodeByKey(c);
			String leadsto = controlNode.getLeadsto();
			if (leadsto != null) {
				GraphNode screenNode = getNodeByKey(leadsto);
				logger.info("Adding edge from control " + controlNode + " to screen " + screenNode);
				graph.addEdge(controlNode, screenNode);
			} else {
				// add edge back to parent
				controlsThatLeadToParent.add(controlNode);
			}
		}

		for (String w : model.getWidgets().keySet()) {
			GraphNode widgetNode = getNodeByKey(w);
			List<String> controlList = widgetNode.getControls();
			logger.info("Widget " + w + " ; " + controlList);
			if (controlList != null) {
				for (String c : controlList) {
					GraphNode controlNode = getNodeByKey(c);
					logger.info("Adding edge from widget " + widgetNode + " to control " + controlNode);
					graph.addEdge(widgetNode, controlNode);
				}
			}
		}
		for (String a : model.getApps().keySet()) {
			GraphNode appNode = getNodeByKey(a);
			List<String> controlList = appNode.getControls();
			List<String> widgetList = appNode.getWidgets();

			if (widgetList != null) {
				for (String w : widgetList) {
					GraphNode widgetNode = getNodeByKey(w);
					logger.info("Adding edge from app " + appNode + " to widget " + appNode);
					graph.addEdge(appNode, widgetNode);
				}
			}

			if (controlList != null) {
				for (String c : controlList) {
					GraphNode controlNode = getNodeByKey(c);
					logger.info("Adding edge from app " + appNode + " to control " + controlNode);
					graph.addEdge(appNode, controlNode);
				}
			}
		}
		for (String s : model.getScreens().keySet()) {
			GraphNode screenNode = getNodeByKey(s);

			List<String> controlList = screenNode.getControls();
			List<String> widgetList = screenNode.getWidgets();
			List<String> appList = screenNode.getApps();

			if (appList != null) {
				for (String a : appList) {
					GraphNode appNode = getNodeByKey(a);
					if (doesNotHaveIncomingEdges(appNode)) {
						logger.info("Adding edge from screen " + screenNode + " to app " + appNode);
						graph.addEdge(screenNode, appNode);
					} else {
						screenNode.addNoEdges(appNode);
					}
				}
			}

			if (widgetList != null) {
				for (String w : widgetList) {
					GraphNode widgetNode = getNodeByKey(w);
					if (doesNotHaveIncomingEdges(widgetNode)) {
						logger.info("Adding edge from screen " + screenNode + " to widget " + widgetNode);
						graph.addEdge(screenNode, widgetNode);
					} else {
						screenNode.addNoEdges(widgetNode);
					}
				}
			}

			if (controlList != null) {
				for (String c : controlList) {
					GraphNode controlNode = getNodeByKey(c);
					if (doesNotHaveIncomingEdges(controlNode)) {
						logger.info("Adding edge from screen " + screenNode + " to control " + controlNode);
						graph.addEdge(screenNode, controlNode);
					} else {
						screenNode.addNoEdges(controlNode);
					}
				}
			}
		}

		for (String p : model.getPages().keySet()) {
			GraphNode pageNode = getNodeByKey(p);
			if (pageNode.getRoot()) {
				rootNode = pageNode;
			}

			List<String> controlList = pageNode.getControls();
			List<String> widgetList = pageNode.getWidgets();
			List<String> appList = pageNode.getApps();
			List<String> screenList = pageNode.getScreens();

			if (screenList != null) {
				for (String s : screenList) {
					GraphNode screenNode = getNodeByKey(s);
					if (doesNotHaveIncomingEdges(screenNode) || screenNode.isDefaultComponent()) {
						logger.info("Adding edge from page " + pageNode + " to screen " + screenNode);
						graph.addEdge(pageNode, screenNode);
					} else {
						pageNode.addNoEdges(screenNode);
					}
					screenToPage.put(s, p);
				}
			}

			if (appList != null) {
				for (String a : appList) {
					GraphNode appNode = getNodeByKey(a);
					if (doesNotHaveIncomingEdges(appNode)) {
						logger.info("Adding edge from page " + pageNode + " to app " + appNode);
						graph.addEdge(pageNode, appNode);
					} else {
						pageNode.addNoEdges(appNode);
					}
				}
			}

			if (widgetList != null) {
				for (String w : widgetList) {
					GraphNode widgetNode = getNodeByKey(w);
					if (doesNotHaveIncomingEdges(widgetNode)) {
						logger.info("Adding edge from page " + pageNode + " to widget " + widgetNode);
						graph.addEdge(pageNode, widgetNode);
					} else {
						pageNode.addNoEdges(widgetNode);
					}
				}
			}

			if (controlList != null) {
				for (String c : controlList) {
					GraphNode controlNode = getNodeByKey(c);
					if (doesNotHaveIncomingEdges(controlNode)) {
						logger.info("Adding edge from page " + pageNode + " to control " + controlNode);
						graph.addEdge(pageNode, controlNode);
					} else {
						pageNode.addNoEdges(controlNode);
					}
				}
			}
		}

		// add edges to controls that haven't been assigned to any edges

		for (GraphNode controlNode : controlsThatLeadToParent) {
			// get incoming edges
			Set<DefaultEdge> edges = graph.incomingEdgesOf(controlNode);

			// note that the edges here should not be more that one

			for (DefaultEdge edge : edges) {
				logger.info("Incoming edges to control " + controlNode + " from " + graph.getEdgeSource(edge));
				graph.addEdge(controlNode, graph.getEdgeSource(edge));
			}

		}

		// resolve preconditions
		resolvePreconditions();

		return graph;

	}

	/**
	 * Adds node to the nameNodeMap.
	 *
	 * @param c
	 *            the c
	 * @param v
	 *            the v
	 */
	private void addToMap(String c, GraphNode v) {
		nameNodeMap.put(c, v);

	}

	/**
	 * Parses the literal (from String)
	 *
	 * @param literalString
	 *            the literal string
	 * @return the literal
	 */
	private Literal parseLiteral(String literalString) {
		String[] parts = literalString.split("%");

		if (parts.length != 3 && parts.length != 4) {
			throw new InvalidClauseException(literalString);
		}

		Literal literal = null;
		String[] temp = parts[0].trim().split(";");
		if (temp.length > 2) {
			throw new InvalidLiteralException("invalid literal " + parts[0].trim());
		}
		String literal_name = temp[0].trim();
		String literal_number = "0";
		if (temp.length == 2) {
			literal_number = temp[1].trim();
		}

		GraphNode node = getNodeByKey(literal_name);

		String action = parts[2].trim();
		boolean isNegation = parts[1].toLowerCase().trim().equals("not");

		if (parts.length == 3) {
			literal = new Literal(node, literal_number, action, isNegation);
		} else {
			literal = new Literal(node, literal_number, action, isNegation, parts[3].trim());
		}
		return literal;
	}

	boolean isValidAction(GraphNode node, String action) {
		// TODO : refine and then enable
		return true;
//		
//		List<String> assertions = node.getImplictAssertions();
//		
//		
//		for (String asrt : assertions) {
//			if (asrt.equals(action)) {
//				return true;
//			}
//		}
//
//		return false;
	}

	/**
	 * Parses the clause (from String).
	 *
	 * @param clauseString
	 *            the clause string
	 * @return the clause
	 */
	private Clause parseClause(String clauseString) {
		Clause clause = new Clause();
		String[] literals = clauseString.split(",");
		for (String literalString : literals) {
			Literal literal = parseLiteral(literalString);
			clause.addLiterals(literal);
		}

		return clause;
	}

	/**
	 * Parses the precondition (from String).
	 *
	 * @param precodtionString
	 *            the precodtion string
	 * @return the explicit assertion
	 */
	public ExplicitAssertion parsePrecondition(List<String> precodtionString) {
		if (precodtionString == null) {
			return null;
		}
		// logger.info("pre-condition " + precodtionString);
		ExplicitAssertion explicitAssertion = new ExplicitAssertion();
		for (String precs : precodtionString) {
			Clause clause = parseClause(precs);
			explicitAssertion.addclauses(clause);
		}

		return explicitAssertion;

	}

	/**
	 * Resolve preconditions (from String).
	 */
	private void resolvePreconditions() {
		for (GraphNode node : nodesWithPrecondition) {
			List<String> precondtionString = node.getPrecondition();

			ExplicitAssertion parsedPrecondition = parsePrecondition(precondtionString);
			node.setParsedPreCondition(parsedPrecondition);
			logger.info(parsedPrecondition);
		}
	}

	/**
	 * Checks if node has incomming edges
	 *
	 * @param node
	 *            the node
	 * @return true, if successful
	 */
	private boolean doesNotHaveIncomingEdges(GraphNode node) {
		Set<DefaultEdge> edges = graph.incomingEdgesOf(node);

		if (edges.size() > 0) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Creates a new control vertex.
	 *
	 * @param control
	 *            the control
	 * @param nodeType
	 *            the node type
	 * @return the graph node
	 * @throws IllegalAccessException
	 *             the illegal access exception
	 * @throws InvocationTargetException
	 *             the invocation target exception
	 */
	private GraphNode createNewVertex(Control control, NodeType nodeType)
			throws IllegalAccessException, InvocationTargetException {
		GraphNode graphNode = new GraphNode();
		BeanUtils.copyProperties(graphNode, control);
		graphNode.setNodeType(nodeType);
		if (graphNode.getPrecondition() != null) {
			nodesWithPrecondition.add(graphNode);
		}
		graphNode.setName(graphNode.getName().replaceAll(" ", ""));
		return graphNode;
	}

	/**
	 * Creates a new widget vertex.
	 *
	 * @param widget
	 *            the widget
	 * @param nodeType
	 *            the node type
	 * @return the graph node
	 * @throws IllegalAccessException
	 *             the illegal access exception
	 * @throws InvocationTargetException
	 *             the invocation target exception
	 */
	private GraphNode createNewVertex(Widget widget, NodeType nodeType)
			throws IllegalAccessException, InvocationTargetException {
		GraphNode graphNode = new GraphNode();
		BeanUtils.copyProperties(graphNode, widget);
		graphNode.setNodeType(nodeType);
		if (graphNode.getPrecondition() != null) {
			nodesWithPrecondition.add(graphNode);
		}
		graphNode.setName(graphNode.getName().replaceAll(" ", ""));
		return graphNode;
	}

	/**
	 * Creates a new app vertex.
	 *
	 * @param app
	 *            the app
	 * @param nodeType
	 *            the node type
	 * @return the graph node
	 * @throws IllegalAccessException
	 *             the illegal access exception
	 * @throws InvocationTargetException
	 *             the invocation target exception
	 */
	private GraphNode createNewVertex(App app, NodeType nodeType)
			throws IllegalAccessException, InvocationTargetException {
		GraphNode graphNode = new GraphNode();
		BeanUtils.copyProperties(graphNode, app);
		graphNode.setNodeType(nodeType);
		if (graphNode.getPrecondition() != null) {
			nodesWithPrecondition.add(graphNode);
		}
		graphNode.setName(graphNode.getName().replaceAll(" ", ""));
		return graphNode;
	}

	/**
	 * Creates a new screen vertex.
	 *
	 * @param screen
	 *            the screen
	 * @param nodeType
	 *            the node type
	 * @return the graph node
	 * @throws IllegalAccessException
	 *             the illegal access exception
	 * @throws InvocationTargetException
	 *             the invocation target exception
	 */
	private GraphNode createNewVertex(Screen screen, NodeType nodeType)
			throws IllegalAccessException, InvocationTargetException {
		GraphNode graphNode = new GraphNode();
		BeanUtils.copyProperties(graphNode, screen);
		graphNode.setNodeType(nodeType);
		if (graphNode.getPrecondition() != null) {
			nodesWithPrecondition.add(graphNode);
		}
		graphNode.setName(graphNode.getName().replaceAll(" ", ""));
		return graphNode;
	}

	/**
	 * Creates a new page vertex.
	 *
	 * @param page
	 *            the page
	 * @param nodeType
	 *            the node type
	 * @return the graph node
	 * @throws IllegalAccessException
	 *             the illegal access exception
	 * @throws InvocationTargetException
	 *             the invocation target exception
	 */
	private GraphNode createNewVertex(Page page, NodeType nodeType)
			throws IllegalAccessException, InvocationTargetException {
		GraphNode graphNode = new GraphNode();
		BeanUtils.copyProperties(graphNode, page);
		graphNode.setNodeType(nodeType);
		if (graphNode.getPrecondition() != null) {
			nodesWithPrecondition.add(graphNode);
		}
		graphNode.setName(graphNode.getName().replaceAll(" ", ""));
		return graphNode;
	}

	/**
	 * Adds implicit assertions to required nodes
	 */
	public void addImplicitAssertions() {
		// get all the nodes
		Set<GraphNode> allNodes = graph.vertexSet();
		for (GraphNode node : allNodes) {
			if (node.getNodeType() == NodeType.PAGE) {
				node.addImplicitAssertion(ConfigReader.pageImplictFunc.get(0));
			} else {
				node.addImplicitAssertion(ConfigReader.intermImplictFunc.get(0));
			}

			// if (node.getNodeType() == NodeType.CONTROL &&
			// node.getAction_type().contains("type")) {
			if (node.getNodeType() == NodeType.CONTROL) {

				for (String funcName : ConfigReader.controlImplictFunc) {
					node.addImplicitAssertion(funcName);
				}
			}
			logger.info("Node " + node + " has assertions : " + node.getImplictAssertions());
		}

	}

	/**
	 * Consistency check for the entire graph
	 */
	public void consistencyCheck() {

		// 1. Isolated nodes not allowed
		Set<GraphNode> allNodes = graph.vertexSet();
		for (GraphNode node : allNodes) {
			GraphPath<GraphNode, DefaultEdge> path = DijkstraShortestPath.findPathBetween(graph, rootNode, node);

			if (path == null) {
				logger.error("No path between " + rootNode + " and " + node + " is " + path);
				throw new InvalidModelException(node.toString() + " is isolated");
			}
		}

		// 2. Nodes must have respective properties initialized
		for (GraphNode node : allNodes) {

			// check urls
			if (node.getNodeType() == NodeType.PAGE) {
				if (node.getUrl() == null) {
					logger.error(node.toString() + " has no url assigned");
					throw new InvalidModelException(node.toString() + " has no url assigned");
				}
			}

			// check ids
			// TODO: enable when all Ids are available
			// if (node.getNodeType() != NodeType.PAGE) {
			// if (node.getId() == null || node.getId().get("by") == null ||
			// node.getId().get("locator") == null) {
			// throw new InvalidModelException(node.toString() + " has no id assigned, [type
			// "+node.getNodeType()+"]");
			// }
			// }

			// check default data
			if (node.getNodeType() == NodeType.CONTROL && node.getAction_type().contains("type")) {
				if (node.getAction_data() == null) {
					logger.error(node.toString() + " has no default data assigned");
					throw new InvalidModelException(node.toString() + " has no default data assigned");
				}
			}
		}

		// check if assertions use valid functions
		for (GraphNode node : allNodes) {

			if (node.getParsedPreCondition() != null) {
				for (Clause cls : node.getParsedPreCondition().getclauses()) {
					for (Literal ltl : cls.getLiterals()) {

						if (!isValidAction(ltl.getNode(), ltl.getAction())) {
							logger.error("The action " + ltl.getAction() + " is not defined for " + ltl.getNode());
							throw new InvalidLiteralException(
									"The action " + ltl.getAction() + " is not defined for " + ltl.getNode());
						}
					}
				}

			}

		}

	}

	/**
	 * Converts graph to dot format, used for visualization
	 *
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void toDot() throws IOException {
		logger.debug("Generating dot file ");

		String graphOutputDir = targetDir + File.separator + "graphs";
		File file = new File(graphOutputDir);
		file.mkdirs();

		String graphFilePath = graphOutputDir + File.separator + "graph.dot";

		ComponentNameProvider<GraphNode> vertexIDProvider = new IntegerComponentNameProvider<GraphNode>();
		ComponentNameProvider<GraphNode> vertexLabelProvider = new StringComponentNameProvider<GraphNode>();

		DOTExporter<GraphNode, DefaultEdge> exporter = new DOTExporter<GraphNode, DefaultEdge>(vertexIDProvider,
				vertexLabelProvider, null);

		logger.info("Writing graph to file " + graphFilePath);
		exporter.exportGraph(graph, new FileWriter(graphFilePath));

		try {
			String pngFilePath = graphOutputDir + File.separator + "graph.png";

			String[] command = { "dot", "-Tpng", graphFilePath, "-o", pngFilePath };
			logger.info("Png conversion  : " + Arrays.toString(command));
			ProcessBuilder probuilder = new ProcessBuilder(command);
			probuilder.start();
		} catch (Exception e) {
			logger.error("Error while generating the graph png");
		}

	}

	/**
	 * Gets the name node map.
	 *
	 * @return the name node map
	 */
	public Map<String, GraphNode> getNameNodeMap() {
		return nameNodeMap;
	}

	/**
	 * Gets the root node.
	 *
	 * @return the root node
	 */
	public GraphNode getRootNode() {
		return rootNode;
	}

	/**
	 * Gets the screen to page.
	 *
	 * @return the screen to page
	 */
	public Map<String, String> getScreenToPage() {
		return screenToPage;
	}

}
