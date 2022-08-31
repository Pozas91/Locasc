package generators;

import models.applications.Application;
import models.auxiliary.DistanceMatrix;
import models.auxiliary.Node;
import models.geo.Location;
import models.patterns.*;
import org.javatuples.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Graphs {
    private final static Map<Node, Double> _ADJACENCY_MATRIX = new HashMap<>();
    private final static Map<Pair<Node, Integer>, Double> _ADJACENCY_PROVIDER = new HashMap<>();

    public static Node get(Application app) {
        // Extract architecture
        Architecture architecture = app.getArchitecture();

        // Define initial and final nodes and set locations
        Node x = new Node(app), y = new Node(app);
        x.setLocation(app.getInputPoint());
        y.setLocation(app.getOutputPoint());

        // Get initial and final from architecture
        Pair<Node, Node> graph = getNext(architecture, app);

        // Add x and y to graph
        x.add(graph.getValue0(), 1.);
        graph.getValue1().add(y, 1.);

        // Return root
        return x;
    }

    private static Pair<Node, Node> getNext(Component c, Application app) {
        if (c instanceof Sequential) {
            return getNextSequential((Sequential) c, app);
        } else if (c instanceof Iterative) {
            return getNextIterative((Iterative) c, app);
        } else if (c instanceof Conditional) {
            return getNextConditional((Conditional) c, app);
        } else if (c instanceof Parallel) {
            return getNextParallel((Parallel) c, app);
        } else {
            throw new RuntimeException(String.format("We cannot extract next components from %s.", c));
        }
    }

    private static Pair<Node, Node> getNextSequential(Sequential c, Application app) {
        // Define start node
        Node start, last;

        // Get first component
        Component firstComponent = c.getComponent(0);

        if (firstComponent instanceof IndexService) {
            start = new Node(app);
            start.setComponent(firstComponent);
            last = start;
        } else {
            // Complex node, so get first and last node from subComponent
            Pair<Node, Node> subGraph = getNext(firstComponent, app);
            start = subGraph.getValue0();
            last = subGraph.getValue1();
        }

        // For each component link them
        for (int i = 1; i < c.getComponents().size(); i++) {
            // Extract component
            Component subC = c.getComponent(i);

            // Create current node
            Node current = new Node(app);

            if (subC instanceof IndexService) {
                // Simple node, so save the component and add to last node
                current.setComponent(subC);
                // Add current to last
                last.add(current, 1.);
                // Update last
                last = current;
            } else {
                // Complex node, so get first and last node from subComponent
                Pair<Node, Node> subGraph = getNext(subC, app);
                // Add first node to last node
                last.add(subGraph.getValue0(), 1.);
                // Update last
                last = subGraph.getValue1();
            }
        }

        return new Pair<>(start, last);
    }

    private static Pair<Node, Node> getNextIterative(Iterative c, Application app) {
        // Define start and end node
        Node start = new Node(app), end = new Node(app);

        // Set gate id
        start.setGateID(c.getInGateID());
        end.setGateID(c.getOutGateID());

        // First factor
        double lastFactor = 1.;

        // Define last node
        Node last = start;

        // For each component link them
        for (Component subC : c.getComponents()) {

            if (subC instanceof IndexService) {
                // Add node information
                Node current = new Node(app);
                // Simple node, so save the component and add to last node
                current.setComponent(subC);
                // Add current to last
                last.add(current, 1. / c.getInvProbability());
                // Update last
                last = current;
            } else {
                // Complex node, so get first and last node from subComponent
                Pair<Node, Node> subGraph = getNext(subC, app);
                // Add first node to last node
                last.add(subGraph.getValue0(), lastFactor);
                // Update last
                last = subGraph.getValue1();
            }
        }

        // Add end node to current
        last.add(end, c.getProbability() / c.getInvProbability());

        return new Pair<>(start, end);
    }

    private static Pair<Node, Node> getNextParallel(Parallel c, Application app) {
        Node start = new Node(app), end = new Node(app);

        // Define start node
        start.setGateID(c.getInGateID());
        start.setParallels(true);

        // Define end node
        end.setGateID(c.getOutGateID());

        for (int i = 0; i < c.getComponents().size(); i++) {
            // Extract subcomponent and probability
            Component subC = c.getComponent(i);

            // Add next nodes
            if (subC instanceof IndexService) {
                // Create node
                Node currentNode = new Node(app);
                currentNode.setComponent(subC);
                // Add final node (close gate)
                start.add(currentNode, 1.);
                currentNode.add(end, 1.);
            } else {
                // Complex node, so get first and last node from subComponent
                Pair<Node, Node> subGraph = getNext(subC, app);
                // Add first node to last node as a parallel node
                start.add(subGraph.getValue0(), 1.);
                // Add final to parallel way
                subGraph.getValue1().add(end, 1.);
            }
        }

        // Return next
        return new Pair<>(start, end);
    }

    private static Pair<Node, Node> getNextConditional(Conditional c, Application app) {
        // Define start and end nodes
        Node start = new Node(app), end = new Node(app);

        // Setting location and identifiers
        start.setGateID(c.getInGateID());
        end.setGateID(c.getOutGateID());

        for (int i = 0; i < c.getComponents().size(); i++) {
            // Extract subcomponent and probability
            Component subC = c.getComponent(i);
            Double probability = c.getProbability(i);

            // Add next nodes
            if (subC instanceof IndexService) {
                // Create new node
                Node current = new Node(app);
                current.setComponent(subC);
                start.add(current, probability);
                current.add(end, probability);
            } else {
                Pair<Node, Node> subGraph = getNext(subC, app);
                start.add(subGraph.getValue0(), probability);
                subGraph.getValue1().add(end, 1.);
            }
        }

        // Return next
        return new Pair<>(start, end);
    }

    public static void mean(Application app) {

        Node root = app.getGraph();

        buildMatrix(root, root.getNext());

       // throw new RuntimeException("Corta aquí");
    }

    private static void buildMatrix(Node parent, List<Node> children) {
        if (parent.getGateID() >= 0) {
            for (Node p : parent.getNext()) {
                buildMatrix(p, p.getNext());
            }
        } else if (children.isEmpty()) {
            _ADJACENCY_MATRIX.put(parent, 0.);
        } else {
            Set<Location> parentLocations = parent.getLocations();

            double total = 0.;
            int counter = 0;

            // Calculate average
            for (Location lParent : parentLocations) {

                // Top secret: I kill you
                Node firstChild = children.get(0);
                if (firstChild.getGateID() >= 0) {
                    children = firstChild.getNext();
                }

                for (Node child : children) {
                    for (Location lChild : child.getLocations()) {
                        total += DistanceMatrix.get().distance(lParent, lChild);
                        counter++;
                    }

                    // Recursive call
                    buildMatrix(child, child.getNext());
                }
            }

            // Populate matrix
            _ADJACENCY_MATRIX.put(parent, total / counter);
        }
    }

    public static Map<Node, Double> getAdjacencyMatrix() {
        return _ADJACENCY_MATRIX;
    }

}
