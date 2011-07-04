package magic.ai;

import java.util.*;

import magic.model.MagicGame;
import magic.model.MagicPlayer;
import magic.model.phase.MagicPhase;
import magic.model.event.MagicEvent;
import magic.model.choice.*;

/*
AI using Monte Carlo Tree Search

Classical MCTS (UCT)
 - use UCB1 formula for selection with C = sqrt(2)
 - reward either 0 or 1
 - backup by averaging
 - uniform random simulated playout
 - score = XX% (25000 matches against MMAB-1)

Enchancements to basic UCT
 - use ratio selection (v + 10)/(n + 10) 
 - UCB1 with C = 1.0
 - UCB1 with C = 2.0
 - UCB1 with C = 3.0
 - use normal bound max(1,v + 2 * std(v))
 - reward depends on length of playout
 - backup by robust max

References:
UCT algorithm from Kocsis and Sezepesvari 2006

Consistency Modifications for Automatically Tuned Monte-Carlo Tree Search
  consistent -> child of root with greatest number of simulations is optimal
  frugal -> do not need to visit the whole tree
  eps-greedy is not consisteny for fixed eps (with prob eps select randomly, else use score)
  eps-greedy is consistent but not frugal if eps dynamically decreases to 0
  UCB1 is consistent but not frugal
  score = average is not consistent
  score = (total reward + K)/(total simulation + 2K) is consistent and frugal!
  using v_t threshold ensures consistency for case of reward in {0,1} using any score function
    v(s) < v_t (0.3), randomy pick a child, else pick child that maximize score

Monte-Carlo Tree Search in Lines of Action 
  1-ply lookahread to detect direct win for player to move
  secure child formula for decision v + A/sqrt(n)
  evaluation cut-off: use score function to stop simulation early
  use evaluation score to remove "bad" moves during simulation
  use evaluation score to keep k-best moves
  mixed: start with corrective, rest of the moves use greedy
*/
public class MCTSAI implements MagicAI {
    
    private static final int MAX_ACTIONS = 10000;

    private final boolean LOGGING;
    private final boolean CHEAT;

    //cache the set of choices at the root to avoid recomputing it all the time
    private List<Object[]> RCHOICES;

    //cache nodes to reuse them in later decision
    private final StateCache<Long, MCTSGameTree> CACHE = new StateCache<Long, MCTSGameTree>(1000);

    public MCTSAI() {
        //default: no logging, cheats
        this(false, true);
    }

    public MCTSAI(final boolean log, final boolean cheat) {
        LOGGING = log || (System.getProperty("debug") != null);
        CHEAT = cheat;
    }

    private void log(final String message) {
        if (LOGGING) {
            System.err.println(message);
        }
    }

    public synchronized Object[] findNextEventChoiceResults(
            final MagicGame startGame, 
            final MagicPlayer scorePlayer) {

        // Determine possible choices
        MagicGame choiceGame = new MagicGame(startGame, scorePlayer);
        final MagicEvent event = choiceGame.getNextEvent();
        RCHOICES = event.getArtificialChoiceResults(choiceGame);
        choiceGame = null;

        final int size = RCHOICES.size();
        final String pinfo = "MCTS " + scorePlayer.getIndex() + " (" + scorePlayer.getLife() + ")";
        
        // No choice
        assert size > 0 : "ERROR! No choice found at start of MCTS";
    
        // Single choice
        if (size == 1) {
            return startGame.map(RCHOICES.get(0));
        }
        
        //normal: max time is 1000 * level
        int MAX_TIME = 1000 * startGame.getArtificialLevel(scorePlayer.getIndex());
        int MAX_SIM = Integer.MAX_VALUE;
        
        //assert enabled: max sim is 1000
        assert (MAX_TIME = Integer.MAX_VALUE) != 1;
        assert (MAX_SIM = 1000) != 1;
        
        final long START_TIME = System.currentTimeMillis();
       
        //root represents the start state
        final MCTSGameTree root = MCTSGameTree.getNode(CACHE, startGame, RCHOICES);

        //end simulations once root is AI win or time is up
        int sims;
        for (sims = 0;
             System.currentTimeMillis() - START_TIME < MAX_TIME &&
             sims < MAX_SIM && 
             !root.isAIWin(); 
             sims++) {
            
            //clone the MagicGame object for simulation
            final MagicGame rootGame = new MagicGame(startGame, scorePlayer);
            if (!CHEAT) {
                rootGame.setKnownCards();
            }
            
            //pass in a clone of the state, 
            //genNewTreeNode grows the tree by one node
            //and returns the path from the root to the new node
            final LinkedList<MCTSGameTree> path = growTree(root, rootGame);
          
            assert path.size() >= 2 : "ERROR! length of MCTS path is " + path.size();

            // play a simulated game to get score
            // update all nodes along the path from root to new node 
            final double score = randomPlay(path.getLast(), rootGame);
            
            // update score and game theoretic value along the chosen path
            MCTSGameTree child = null; 
            MCTSGameTree parent = null;
            while (!path.isEmpty()) {
                child = parent;
                parent = path.removeLast();

                parent.updateScore(child, score);

                if (child != null && child.isSolved()) {
                    final int steps = child.getSteps() + 1;
                    if (parent.isAI() && child.isAIWin()) {
                        parent.setAIWin(steps);
                    } else if (parent.isOpp() && child.isAILose()) {
                        parent.setAILose(steps);
                    } else if (parent.isAI() && child.isAILose()) {
                        parent.incLose(steps);
                    } else if (parent.isOpp() && child.isAIWin()) {
                        parent.incLose(steps);
                    } 
                }
            }
        }

        assert root.size() > 0 : "ERROR! Root has no children but there are " + size + " choices";

        //select the best child/choice
        final MCTSGameTree first = root.first();
        double maxD = first.getDecision();
        int bestC = first.getChoice();
        for (MCTSGameTree node : root) {
            final double D = node.getDecision();
            final int C = node.getChoice();
            if (D > maxD) { 
                maxD = D;
                bestC = C;
            }
        }
        final Object[] selected = RCHOICES.get(bestC); 

        if (LOGGING) {
            final long duration = System.currentTimeMillis() - START_TIME;
            log("MCTS:\ttime: " + duration + 
                     "\tsims: " + (root.getNumSim() - sims) + "+" + sims);
            log(pinfo);
            for (MCTSGameTree node : root) {
                final StringBuffer out = new StringBuffer();
                if (node.getChoice() == bestC) {
                    out.append("* ");
                } else {
                    out.append("  ");
                }
                out.append('[');
                out.append((int)(node.getV() * 100));
                out.append('/');
                out.append(node.getNumSim());
                out.append('/');
                if (node.isAIWin()) {
                    out.append("win");
                    out.append(':');
                    out.append(node.getSteps());
                } else if (node.isAILose()) {
                    out.append("lose");
                    out.append(':');
                    out.append(node.getSteps());
                } else {
                    out.append("?");
                }
                out.append(']');
                out.append(CR2String(RCHOICES.get(node.getChoice())));
                log(out.toString());
            }
        }

        return startGame.map(selected);
    }

    private LinkedList<MCTSGameTree> growTree(final MCTSGameTree root, final MagicGame game) {
        final LinkedList<MCTSGameTree> path = new LinkedList<MCTSGameTree>();
        boolean found = false;
        MCTSGameTree curr = root;
        path.add(curr);

        for (List<Object[]> choices = getNextChoices(game, false);
             choices != null;
             choices = getNextChoices(game, false)) {

            assert choices.size() > 0 : "ERROR! No choice at start of genNewTreeNode";
            
            assert !curr.hasDetails() || MCTSGameTree.checkNode(curr, choices) : 
                "ERROR! Inconsistent node found" + "\n" + game + printPath(path) + MCTSGameTree.printNode(curr, choices);
          
            final MagicEvent event = game.getNextEvent();
           
            //first time considering the choices available at this node, 
            //fill in additional details for curr
            if (!curr.hasDetails()) {
                curr.setIsAI(game.getScorePlayer() == event.getPlayer());
                curr.setMaxChildren(choices.size());
                assert curr.setChoicesStr(choices);
            }

            //look for first non root AI node along this path and add it to cache
            if (!found && curr != root && curr.isAI()) {
                found = true;
                assert curr.isCached() || printPath(path);
                MCTSGameTree.addNode(CACHE, game, curr);
            }

            //there are unexplored children of node
            //assume we explore children of a node in increasing order of the choices
            if (curr.size() < choices.size()) {
                final int idx = curr.size();
                Object[] choice = choices.get(idx);
                game.executeNextEvent(choice);
                final MCTSGameTree child = new MCTSGameTree(curr, idx, game.getScore()); 
                assert (child.desc = MCTSGameTree.obj2String(choice[0])).equals(child.desc);
                curr.addChild(child);
                path.add(child);
                return path;
            
            //all the children are in the tree, find the "best" child to explore
            } else {

                assert curr.size() == choices.size() : "ERROR! Different number of choices in node and game" + 
                    printPath(path) + MCTSGameTree.printNode(curr, choices); 

                MCTSGameTree next = curr.first();
                double bestS = next.getUCT();
                for (MCTSGameTree child : curr) {
                    final double raw = child.getUCT();
                    final double S = child.modify(raw);
                    if (S > bestS) {
                        bestS = S;
                        next = child;
                    }
                }

                //move down the tree
                curr = next;
               
                //update the game state and path
                game.executeNextEvent(choices.get(curr.getChoice()));
                path.add(curr);
            }
        } 
       
        return path;
    }

    //returns a reward in the range [0, 1]
    private double randomPlay(final MCTSGameTree node, final MagicGame game) {
        //terminal node, no need for random play
        if (game.isFinished()) {
            assert game.getLosingPlayer() != null : "ERROR! Game finished but no losing player";
            
            if (game.getLosingPlayer() == game.getScorePlayer()) {
                node.setAILose(0);
                return 0.0;
            } else {
                node.setAIWin(0);
                return 1.0;
            }
        }

        final int startActions = game.getNumActions();
        getNextChoices(game, true);
        final int actions = game.getNumActions() - startActions;

        if (game.getLosingPlayer() == null) {
            return 0.5;
        } else if (game.getLosingPlayer() == game.getScorePlayer()) {
            return actions/(2.0 * MAX_ACTIONS);
        } else {
            return 1.0 - actions/(2.0 * MAX_ACTIONS);
        }
    }
    
    private List<Object[]> getNextChoices(
            final MagicGame game, 
            final boolean sim) {
        
        final int startActions = game.getNumActions();

        //use fast choices during simulation
        game.setFastChoices(sim);
        
        // simulate game until it is finished or simulated MAX_ACTIONS actions
        while (!game.isFinished() && 
               (game.getNumActions() - startActions) < MAX_ACTIONS) {
            //do not accumulate score down the tree
            game.setScore(0);

            if (!game.hasNextEvent()) {
                game.getPhase().executePhase(game);
                continue;
            }

            //game has next event
            final MagicEvent event = game.getNextEvent();

            if (!event.hasChoice()) {
                game.executeNextEvent(MagicEvent.NO_CHOICE_RESULTS);
                continue;
            }
            
            //event has choice

            if (sim) {
                //get simulation choice and execute
                final Object[] choice = event.getSimulationChoiceResult(game);
                assert choice != null : "ERROR! No choice found during MCTS sim";
                game.executeNextEvent(choice);
            } else {
                //get list of possible AI choices
                List<Object[]> choices = null;
                if (game.getNumActions() == 0) {
                    //map the RCHOICES to the current game instead of recomputing the choices
                    choices = new ArrayList<Object[]>(RCHOICES.size());
                    for (Object[] choice : RCHOICES) {
                        choices.add(game.map(choice));
                    }
                } else {
                    choices = event.getArtificialChoiceResults(game);
                }
                assert choices != null;
                
                final int size = choices.size();
                assert size > 0 : "ERROR! No choice found during MCTS getACR";
                
                if (size == 1) {
                    //single choice
                    game.executeNextEvent(choices.get(0));
                } else {
                    //multiple choice
                    return choices;
                }
            }
        }
        
        //game is finished or number of actions > MAX_ACTIONS
        return null;
    }
    
    private static String CR2String(Object[] choiceResults) {
        final StringBuffer buffer=new StringBuffer();
        if (choiceResults!=null) {
            buffer.append(" (");
            boolean first=true;
            for (final Object choiceResult : choiceResults) {
                if (first) {
                    first=false;
                } else {
                    buffer.append(',');
                }
                buffer.append(choiceResult);
            }
            buffer.append(')');
        }
        return buffer.toString();
    }

    private boolean printPath(final List<MCTSGameTree> path) {
        StringBuffer sb = new StringBuffer();
        for (MCTSGameTree p : path) {
            sb.append(" -> ").append(p.desc);
        }
        log(sb.toString());
        return true;
    }

}

//each tree node stores the choice from the parent that leads to this node
class MCTSGameTree implements Iterable<MCTSGameTree> {
   
    private final MCTSGameTree parent;
    private final LinkedList<MCTSGameTree> children = new LinkedList<MCTSGameTree>();
    private final int choice;
    private boolean isAI;
    private boolean isCached = false;
    private int maxChildren = -1;
    private int numLose = 0;
    private int numSim = 0;
    private int evalScore = 0;
    private int steps = 0;
    private double sum = 0;
    private double S = 0;
    public String desc;
    public String[] choicesStr;
    
    //min sim for using robust max
    private int maxChildSim = 100;     
    
    public MCTSGameTree(final MCTSGameTree parent, final int choice, final int evalScore) { 
        this.evalScore = evalScore;
        this.choice = choice;
        this.parent = parent;
    }
    
    private static boolean log(final String message) {
        System.err.println(message);
        return true;
    }
    
    static public int obj2StringHash(Object obj) {
        return obj2String(obj).hashCode();
    }

    static public String obj2String(Object obj) {
        if (obj == null) {
            return "null";
        } else if (obj instanceof MagicBuilderPayManaCostResult) {
            return ((MagicBuilderPayManaCostResult)obj).getText();
        } else {
            return obj.toString();
        }
    }
    
    static void addNode(
            final StateCache<Long, MCTSGameTree> cache, 
            final MagicGame game, 
            final MCTSGameTree node) {
        if (node.isCached()) {
            return;
        }
        final long gid = game.getGameId();
        cache.put(gid, node);
        node.setCached();
        assert log("ADDED: " + game.getIdString());
    }

    static MCTSGameTree getNode(
            final StateCache<Long, MCTSGameTree> cache, 
            final MagicGame game, 
            final List<Object[]> choices) {
        final long gid = game.getGameId();
        MCTSGameTree candidate = cache.get(gid);
        
        if (candidate != null) { 
            assert log("CACHE HIT");
            assert log("HIT  : " + game.getIdString());
            assert printNode(candidate, choices);
            return candidate;
        } else {
            assert log("CACHE MISS");
            assert log("MISS : " + game.getIdString());
            final MCTSGameTree root = new MCTSGameTree(null, -1, -1);
            assert (root.desc = "root").equals(root.desc);
            return root;
        }
    }
               
    static boolean checkNode(final MCTSGameTree curr, List<Object[]> choices) {
        if (curr.getMaxChildren() != choices.size()) {
            return false;
        }
        for (int i = 0; i < choices.size(); i++) {
            final String checkStr = obj2String(choices.get(i)[0]);
            if (!curr.choicesStr[i].equals(checkStr)) {
                return false;
            }
        }
        for (MCTSGameTree child : curr) {
            final String checkStr = obj2String(choices.get(child.getChoice())[0]);
            if (!child.desc.equals(checkStr)) {
                return false;
            }
        }
        return true;
    }
                    
    
    static boolean printNode(final MCTSGameTree curr, List<Object[]> choices) {
        if (curr.choicesStr != null) {
            for (String str : curr.choicesStr) {
                log("PAREN: " + str);
            }
        } else {
            log("PAREN: not defined");
        }
        for (MCTSGameTree child : curr) {
            log("CHILD: " + child.desc);
        }
        for (Object[] choice : choices) {
            log("GAME : " + obj2String(choice[0]));
        }
        return true;
    }


    public boolean isCached() {
        return isCached;
    }

    public void setCached() {
        isCached = true;
    }

    public boolean hasDetails() {
        return maxChildren != -1;
    }

    public boolean setChoicesStr(List<Object[]> choices) {
        choicesStr = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            choicesStr[i] = obj2String(choices.get(i)[0]);
        }
        return true;
    }

    public void setMaxChildren(final int mc) {
        maxChildren = mc;
    }
    
    public int getMaxChildren() {
        return maxChildren;
    }

    public boolean isAI() {
        return isAI;
    }

    public boolean isOpp() {
        return !isAI;
    }
    
    public void setIsAI(final boolean ai) {
        this.isAI = ai;
    }

    public boolean isSolved() {
        return evalScore == Integer.MAX_VALUE || evalScore == Integer.MIN_VALUE;
    }
    
    public void updateScore(final MCTSGameTree child, final double delta) {
        final double oldMean = (numSim > 0) ? sum/numSim : 0;
        sum += delta;
        numSim += 1;
        final double newMean = sum/numSim;
        S += (delta - oldMean) * (delta - newMean);   

        //if child has sufficient simulations, backup using robust max instead of average
        if (child != null && child.getNumSim() > maxChildSim) {
            maxChildSim = child.getNumSim();
            if (isAI && child.getAvg() > getAvg()) {
                sum = child.getAvg() * numSim;
            }
            if (!isAI && child.getAvg() < getAvg()) {
                sum = child.getAvg() * numSim;
            }
        }
    }
    
    public double getUCT() {
        final double C = 1.41421;
        return getV() + C * Math.sqrt(Math.log(parent.getNumSim()) / getNumSim());
    }
    
    public double getRatio() {
        final double K = 1.0;
        return (getSum() + K)/(getNumSim() + 2*K);
    }

    public double getNormal() {
        return Math.max(1.0, getV() + 2 * Math.sqrt(getVar()));
    }
    
    //decrease score of lose node, boost score of win nodes
    public double modify(final double sc) {
        if ((!parent.isAI() && isAIWin()) || (parent.isAI() && isAILose())) {
            return sc - 2.0;
        } else if ((parent.isAI() && isAIWin()) || (!parent.isAI() && isAILose())) {
            return sc + 2.0;
        } else {
            return sc; 
        } 
    }

    public double getVar() {
        final int MIN_SAMPLES = 10;
        if (numSim < MIN_SAMPLES) {
            return 1.0;
        } else {
            return S/(numSim - 1);
        }
    }

    public boolean isAIWin() {
        return evalScore == Integer.MAX_VALUE;
    }
    
    public boolean isAILose() {
        return evalScore == Integer.MIN_VALUE;
    }

    public void incLose(final int lsteps) {
        numLose++;
        steps = Math.max(steps, lsteps);
        if (numLose == maxChildren) {
            if (isAI) {
                setAILose(steps);
            } else {
                setAIWin(steps);
            }
        }
    }

    public int getChoice() {
        return choice;
    }
    
    public int getSteps() {
        return steps;
    }

    public void setAIWin(final int steps) {
        this.evalScore = Integer.MAX_VALUE;
        this.steps = steps;
    }

    public void setAILose(final int steps) {
        evalScore = Integer.MIN_VALUE;
        this.steps = steps;
    }

    public int getEvalScore() {
        return evalScore;
    }
    
    public double getDecision() {
        //boost decision score of win nodes by BOOST 
        final int BOOST = 1000000;
        if (isAIWin()) {
            return BOOST + getNumSim();
        } else if (isAILose()) {
            return getNumSim();
        } else {
            return getNumSim();
        }
    }
    
    public int getNumSim() {
        return numSim;
    }
    
    public double getSum() {
        return parent.isAI() ? sum : 1.0 - sum;
    }
    
    public double getAvg() {
        return sum / numSim;
    }

    public double getV() {
        return getSum() / numSim;
    }
   
    public double getSecureScore() {
        return getV() + 1.0/Math.sqrt(numSim);
    }

    public void addChild(MCTSGameTree child) {
        assert children.size() < maxChildren : "ERROR! Number of children nodes exceed maxChildren";
        children.add(child);
    }
    
    public void removeLast() {
        children.removeLast();
    }
    
    public MCTSGameTree first() {
        return children.get(0);
    }
    
    public Iterator<MCTSGameTree> iterator() {
        return children.iterator();
    }

    public int size() {
        return children.size();
    }
}

