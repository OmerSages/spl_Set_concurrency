package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ex.Player.State;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /*
     * The list of cards that are on the table
     */
    private List<Integer> dealersDeck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime;

    private Queue<Integer> waitingPlayers;
    private final long SLEEP_TIME=10;
    private long startTime = System.currentTimeMillis(); //just for config.turnTimeoutMillis = 0 mode


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        dealersDeck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        //TODO
        reshuffleTime = System.currentTimeMillis()+env.config.turnTimeoutMillis;
        waitingPlayers = new LinkedList<Integer>();

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
      
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        //start players threads
        for(Player player: this.players){
            player.getPlayerThread().start();
        }

        while (!shouldFinish()) {
            replaceAllTable();
            updateTimerDisplay(true);
            timerLoop();
        }

        if(env.config.turnTimeoutMillis>0)
            env.ui.setCountdown(0, false);
        clearTable();
        announceWinners();

        //terminate players threads
        try{
            for(int i = players.length-1; i>=0; i--){
                players[i].terminate();
                players[i].getPlayerThread().interrupt();
                players[i].getPlayerThread().join();
            }
        }
        catch(InterruptedException ex){}

        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        long mode = env.config.turnTimeoutMillis;
        while (!terminate && ((mode >0 && System.currentTimeMillis() < reshuffleTime)||(mode<=0 && env.util.findSets(table.cardsOnTable(), 1).size()>0))) {
            sleepUntilWokenOrTimeout();
            checkSetQueue();      
            updateTimerDisplay(false);
        }
    }

    private void checkSetQueue(){
        if(!waitingPlayers.isEmpty())
            {
                Integer playerId;
                synchronized(this){playerId = waitingPlayers.poll();}

                //create the set of cards the player had chosen              
                synchronized(players[playerId]){
                    int[] set = table.playerTokens(playerId);
                    if(set.length==env.config.featureSize){
                        if(env.util.testSet(set)){
                            players[playerId].state = State.Point;
                            replaceSetTable(set);
                            updateTimerDisplay(true);
                        }
                        else
                            players[playerId].state = State.Penalty;
                    }
                    players[playerId].notify();
                }
            }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        this.terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     * @param cards - cards to remove
     * @return - return slots that were now cleared
     */
    private int[] removeCardsFromTable(int[] cards) {
        // TODO implement
        for(Integer card: cards)
            deck.remove(card);
        return table.removeCards(cards);  
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     * @param- the empty slots to fill with new cards
     */
    private void placeCardsOnTable(int[] slots) {
        // TODO implement
        int numOfCardsInDeck = dealersDeck.size();
        for(int i=0; i < slots.length && i<numOfCardsInDeck; i++){
            int card = dealersDeck.remove(0);
            table.placeCard(card, slots[i]); //place the card on the table
        }  
    }

    public void placeCardsOnTable(){
        int numOfCardsInDeck = dealersDeck.size();
        for(int i=0; i < env.config.tableSize && i<numOfCardsInDeck; i++){
            int card = dealersDeck.remove(0);
            table.placeCard(card, i); //place the card on the table
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        try{
            Thread.sleep(SLEEP_TIME);
        }
        catch(InterruptedException ex){}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        long currentSystemTime = System.currentTimeMillis();
        if(env.config.turnTimeoutMillis>0){
            if(reset){
                reshuffleTime = currentSystemTime+env.config.turnTimeoutMillis;
                env.ui.setCountdown(env.config.turnTimeoutMillis,false);
            }
            else
                env.ui.setCountdown(Math.max(reshuffleTime-currentSystemTime, 0),reshuffleTime-currentSystemTime<=env.config.turnTimeoutWarningMillis);
        }
        if(env.config.turnTimeoutMillis==0){
            if(reset)
                this.startTime = System.currentTimeMillis();
            env.ui.setElapsed(System.currentTimeMillis()-this.startTime);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        
        List<Integer> cardsToRemove = table.removeAllCardsFromTable();
        if(cardsToRemove.size()>0)
            dealersDeck.addAll(cardsToRemove);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int winningPoint = 0;
        //check highest score
        for(Player player: players)
            if(player.getScore()>winningPoint)
                winningPoint = player.getScore();
        ArrayList<Integer> winnersArray = new ArrayList<Integer>();
        //find winning players
        for(Player player: players)
            if(player.getScore() == winningPoint)
                winnersArray.add(player.id);
        int[] winners = winnersArray.stream().mapToInt(Integer::intValue).toArray();
        env.ui.announceWinner(winners);     
    }

    /**
     * 
     * @param set - list of the slots the player has chosen has a set
     */
    public synchronized void acceptPlayerSet(Integer playerId){
        //TODO
        this.waitingPlayers.add(playerId);
    }

    public void clearPlayersQueues(){
        for(Player player: players){
            player.clearQueue();
        }
    }

    /**
     * replace a correct set with new cards from the deck
     * @param cards - a set of cards
     */
    public void replaceSetTable(int[] set){

        //acquire lock
        table.tableLock.writeLock().lock();

        //remove set from table
        int[] avaibleSlots = removeCardsFromTable(set);

        //place new cards on table
        placeCardsOnTable(avaibleSlots);

        //print hints if needed
        if(env.config.hints)
            table.hints();

        //release lock
        table.tableLock.writeLock().unlock();
    }

    // clear the table and place new cards
    public void replaceAllTable(){

        //acquire lock
        table.tableLock.writeLock().lock();

        //remove cards from the table back to dealer's deck and shuffle
        removeAllCardsFromTable();
        Collections.shuffle(dealersDeck); 

        //place new cards on table
        placeCardsOnTable();

        //print hints if needed
        if(env.config.hints)
            table.hints();

        //clear queues
        clearPlayersQueues(); //players presses queue
        waitingPlayers.clear(); //sets queue
        for(Player player: players){
            synchronized(player){
                player.notify();
            }
        }          

        //release lock
        table.tableLock.writeLock().unlock();
    }

    public Queue<Integer> getWaitingPlayers(){
        return this.waitingPlayers;
    }
    public int getDealerDeckSize (){
        return dealersDeck.size();
    }

    public void clearTable(){
        //acquire lock
        table.tableLock.writeLock().lock();

        //remove cards from the table
        removeAllCardsFromTable();

        //release lock
        table.tableLock.writeLock().unlock();
    }
}
