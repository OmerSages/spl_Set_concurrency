package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * Mapping between a slot to the players that have a token on it
     */
    private List<Integer>[] slotToPlayers;
    /**
     * Mapping between a player to the slots he has tokens on
     */
    private List<Integer>[] playerToSlots; 


    public final ReentrantReadWriteLock tableLock = new ReentrantReadWriteLock(true);


    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        
    }

    /**
     * Constructor for ourTesting.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     * @param slotToPlayers - mapping between a slot to the players that have a token on it
     * @param playerToSlots - mapping between a player to the slots he has tokens on
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot,ArrayList<Integer>[] slotToPlayers,ArrayList<Integer>[] playerToSlots) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.slotToPlayers = slotToPlayers;
        this.playerToSlots = playerToSlots;
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);

        this.slotToPlayers = new List[slotToCard.length];
        for(int i=0; i<slotToPlayers.length; i++)
            slotToPlayers[i] = new CopyOnWriteArrayList<>();

        this.playerToSlots = new List[env.config.players];
        for(int i=0; i<playerToSlots.length; i++)
            playerToSlots[i] = new CopyOnWriteArrayList<>();
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        // TODO implement
        
        env.ui.placeCard(card,slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) { 
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        // TODO implement

        int removedCard = slotToCard[slot];
        cardToSlot[removedCard] = null;
        slotToCard[slot] = null;

        //clear tokens from card
        for(int player: slotToPlayers[slot])
            playerToSlots[player].remove(new Integer(slot));
        env.ui.removeTokens(slot);
        slotToPlayers[slot].clear();

        env.ui.removeCard(slot);
       
    }

    /**
     * Placesall the cards that are on the table a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        // TODO implement
        
        if(tableLock.readLock().tryLock()){
            if(slotToCard[slot] != null){
                slotToPlayers[slot].add(player);
                playerToSlots[player].add(slot);
                env.ui.placeToken(player, slot);
            }
            tableLock.readLock().unlock();
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        // TODO implement
        boolean removed = false;
        if(tableLock.readLock().tryLock()){
            if(playerToSlots[player].remove(new Integer(slot))){
                env.ui.removeToken(player, slot);
                slotToPlayers[slot].remove(new Integer(player));
                removed = true;
            }
            tableLock.readLock().unlock();
        }
        return removed;
    }

    /**
     * @return - list with the indexes of empty slots in slotToCard
     */
    public List<Integer> emptySlotsIndex()
    {
        List<Integer> slotsIdx = new ArrayList<Integer>();
        for(int i=0; i<slotToCard.length;i++){
            if(slotToCard[i]==null)
                slotsIdx.add(i);
        }
        return slotsIdx;
    }

    /** clear the table
     * @return - a list with all the cards that were on the table
     */
    public List<Integer> removeAllCardsFromTable(){

        List<Integer> cards = new ArrayList<Integer>();
        for(int i=0; i<slotToCard.length; i++){
            if(slotToCard[i]!=null){
                cards.add(slotToCard[i]);
                removeCard(i);
            }
        }
 
        return cards;
    }

    public int[] playerTokens(Integer player){
        int[] cards = new int[playerToSlots[player].size()];
        for(int i=0; i<playerToSlots[player].size(); i++)
            cards[i] = slotToCard[playerToSlots[player].get(i)];
        return cards;
    }

    public boolean completedSet(int playerId){
        return playerToSlots[playerId].size()==env.config.featureSize;
    }
    
    /**
     * remove a correct set from the table
     * @param cards - the set of cards to remove from the table
     * @return - an array of slots that have now been cleared
     */
    public int[] removeCards(int[] cards){
        int[] slots = new int[cards.length];
        for(int i=0; i<cards.length; i++){
            slots[i] = cardToSlot[cards[i]];
            removeCard(cardToSlot[cards[i]]);
        }
        return slots;
    }

    public List<Integer> cardsOnTable(){
        return Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
