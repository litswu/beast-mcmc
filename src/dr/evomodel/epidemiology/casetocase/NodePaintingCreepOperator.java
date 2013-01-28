package dr.evomodel.epidemiology.casetocase;

import dr.evolution.tree.NodeRef;
import dr.evomodel.tree.TreeModel;
import dr.inference.operators.OperatorFailedException;
import dr.inference.operators.SimpleMCMCOperator;
import dr.math.MathUtils;
import dr.xml.*;

import java.util.HashSet;

/**
 * Created with IntelliJ IDEA.
 * User: mhall
 * Date: 24/01/2013
 * Time: 16:18
 * To change this template use File | Settings | File Templates.
 */
public class NodePaintingCreepOperator extends SimpleMCMCOperator {

    public static final String NODE_PAINTING_CREEP_OPERATOR = "nodePaintingCreepOperator";
    private CaseToCaseTransmissionLikelihood c2cLikelihood;

    public NodePaintingCreepOperator(CaseToCaseTransmissionLikelihood c2cLikelihood, double weight){
        this.c2cLikelihood = c2cLikelihood;
        setWeight(weight);
    }

    public String getOperatorName(){
        return NODE_PAINTING_CREEP_OPERATOR;
    }

    public String getPerformanceSuggestion(){
        return "Not implemented";
    }

    public double doOperation() throws OperatorFailedException {
        int oldUnlockedNodes = 0;
        TreeModel tree = c2cLikelihood.getTree();
        for(int i=0; i<tree.getInternalNodeCount(); i++){
            if(c2cLikelihood.getCreepLocks()[i]){
                oldUnlockedNodes++;
            }
        }
        int internalNodeCount = tree.getInternalNodeCount();
        int nodeToSwitch = MathUtils.nextInt(internalNodeCount);
        // Cannot apply this operator if the node is locked to it
        while(c2cLikelihood.getCreepLocks()[nodeToSwitch]
                && c2cLikelihood.getBranchMap()[nodeToSwitch]==c2cLikelihood.getBranchMap()[tree.getRoot().getNumber()]){
            nodeToSwitch = MathUtils.nextInt(internalNodeCount);
        }
        NodeRef node = tree.getInternalNode(nodeToSwitch);
        adjustTree(tree, node, c2cLikelihood.getBranchMap(), c2cLikelihood.getRecalculationArray());
        int newUnlockedNodes = 0;
        for(int i=0; i<tree.getInternalNodeCount(); i++){
            if(c2cLikelihood.getCreepLocks()[i]){
                newUnlockedNodes++;
            }
        }
        return 0;
    }

    private void adjustTree(TreeModel tree, NodeRef node, AbstractCase[] map, boolean[] flags){
        AbstractCase currentCase = map[node.getNumber()];
        if(c2cLikelihood.tipLinked(node)){
            HashSet<Integer> nodesToChange = c2cLikelihood.samePaintingUpTree(node, true);
            NodeRef currentAncestor = node;
            // Find the painting of the first ancestor that doesn't have the same painting
            while(map[currentAncestor.getNumber()]==currentCase){
                currentAncestor = tree.getParent(node);
            }
            // Make the changes and adjust switch locks
            AbstractCase newCase = map[currentAncestor.getNumber()];
            c2cLikelihood.changeSwitchLock(currentAncestor.getNumber(),true);
            for(int i:nodesToChange){
                map[i]=newCase;
                c2cLikelihood.changeSwitchLock(i,true);
            }
            // Adjust creep locks - any nodes with two children of the same painting, and any ancestors of those nodes
            // that are not tip-linked, and any descendants of those nodes which are not, need to be locked.
            if(c2cLikelihood.countChildrenWithSamePainting(currentAncestor)==2){



            }
        } else {
            HashSet<Integer> nodesToChange = c2cLikelihood.samePaintingDownTree(node, true);
            nodesToChange.add(node.getNumber());
            NodeRef descendant = node;
            while(c2cLikelihood.countChildrenWithSamePainting(descendant)!=0){
                if(c2cLikelihood.countChildrenWithSamePainting(descendant)>1){
                    throw new RuntimeException("A node that should be creep-locked is not.");
                } else {
                    for(int i=0; i<tree.getChildCount(descendant);i++){
                        if(map[tree.getChild(descendant,i).getNumber()]==currentCase){
                            descendant=tree.getChild(descendant,i);
                        }
                    }
                }
            }
            int choice = MathUtils.nextInt(1);
            AbstractCase replacementPainting = map[tree.getChild(descendant,choice).getNumber()];
            for(Integer i:nodesToChange){
                map[i]=replacementPainting;
            }

        }
    }

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser(){

        public String getParserName(){
            return NODE_PAINTING_CREEP_OPERATOR;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            CaseToCaseTransmissionLikelihood ftLikelihood =
                    (CaseToCaseTransmissionLikelihood) xo.getChild(CaseToCaseTransmissionLikelihood.class);

            if(!ftLikelihood.isExtended()) {
                throw new XMLParseException("Only extended node paintings use the creep operator.");
            }

            final double weight = xo.getDoubleAttribute("weight");
            return new NodePaintingSwitchOperator(ftLikelihood, weight);
        }

        public String getParserDescription(){
            return "This operator switches the painting of a random eligible internal node from the painting of its " +
                    "first ancestor that has a different painting, or reverses this process";
        }

        public Class getReturnType() {
            return NodePaintingCreepOperator.class;
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private final XMLSyntaxRule[] rules = {
                AttributeRule.newDoubleRule("weight"),
                new ElementRule(CaseToCaseTransmissionLikelihood.class),
        };
    };

}
