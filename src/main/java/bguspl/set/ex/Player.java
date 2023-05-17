package bguspl.set.ex;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import bguspl.set.Env;



/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private Dealer dealer;
    private LinkedBlockingQueue<Integer> keyPresses; //the human/AI key presses

    private final long SECOND=900;//to prevent jump timing
    enum State
    {
        Waiting,
        Availble,
        Penalty,
        Point
    }

    protected State state = State.Availble;
  

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;

        this.playerThread = new Thread(this,"Player "+this.id);
        
        this.dealer = dealer;
        keyPresses = new LinkedBlockingQueue<Integer>(env.config.featureSize-1);

    }

    public Thread getPlayerThread(){
        return this.playerThread;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
           
            // TODO implement main player loop
            try{
                synchronized(keyPresses){
                    while(keyPresses.isEmpty()) 
                        keyPresses.wait(); 
                }                  

                Integer chosenSlot = keyPresses.take();

                if(! table.removeToken(id, chosenSlot))
                {
                    if(!table.completedSet(id)){ //check if the player can use another token
                        table.placeToken(id, chosenSlot);
                        if(table.completedSet(id)){
                            this.state = State.Waiting;
                            synchronized(this){
                                dealer.acceptPlayerSet(id);

                                wait(); //wait for dealer's response

                                switch(this.state)
                                {
                                    case Point: point(); break;
                                    case Penalty: penalty(); break;
                                    default: break;
                                }
                            }
                            clearQueue();
                            this.state = State.Availble;
                        }
                    }
                }
            }
            catch(InterruptedException e){};
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                try{
                    int randomSlot = (int)(Math.random()*env.config.tableSize);
                    keyPressed(randomSlot);
                }
                catch(Exception e){}
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        this.terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if(this.state.equals(State.Availble)){
            synchronized(keyPresses){
                keyPresses.offer(slot);
                keyPresses.notifyAll();
            }
        }           
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        try{ 
            long freezeUntil = System.currentTimeMillis()+env.config.pointFreezeMillis;
            env.ui.setFreeze(id, env.config.pointFreezeMillis);
            while(System.currentTimeMillis()<freezeUntil){
                Thread.sleep(SECOND);
                env.ui.setFreeze(id, freezeUntil-System.currentTimeMillis());
            }
        }
        catch(InterruptedException ex){};      
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        try{
            long freezeUntil = System.currentTimeMillis()+env.config.penaltyFreezeMillis;
            env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
            while(System.currentTimeMillis()<freezeUntil){
                Thread.sleep(SECOND);
                env.ui.setFreeze(id, freezeUntil-System.currentTimeMillis());
            }
        }
        catch(InterruptedException ex){};
    }

    public int getScore() {
        return score;
    }

    public void clearQueue(){
        synchronized(keyPresses){
            this.keyPresses.clear();
        }     
    }

    public int getId(){
        return id;
    }

    public LinkedBlockingQueue<Integer> getPressesQueue(){
        return this.keyPresses;
    }
    
    public boolean getTerminate (){
        return terminate;
    }
}
