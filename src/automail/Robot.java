package automail;

import exceptions.ExcessiveDeliveryException;
import exceptions.ItemTooHeavyException;
import simulation.Building;
import simulation.Clock;
import simulation.IMailDelivery;

/**
 * The robot delivers mail!
 */
public class Robot {

    static public final int INDIVIDUAL_MAX_WEIGHT = 2000;

    IMailDelivery delivery;
    protected final String id;
    /** Possible states the robot can be in */
    public enum RobotState { DELIVERING, WAITING, RETURNING }
    public RobotState current_state;
    private int current_floor;
    private int destination_floor;
    private MailPool mailPool;
    private boolean receivedDispatch;

    private MailItem deliveryItem = null;
    private MailItem tube = null;

    private int deliveryCounter;


    /**
     * Initiates the robot's location at the start to be at the mailroom
     * also set it to be waiting for mail.
     * @param behaviour governs selection of mail items for delivery and behaviour on priority arrivals
     * @param delivery governs the final delivery
     * @param mailPool is the source of mail items
     */
    public Robot(IMailDelivery delivery, MailPool mailPool, int number){
	this.id = "R" + number;
        // current_state = RobotState.WAITING;
	current_state = RobotState.RETURNING;
        current_floor = Building.MAILROOM_LOCATION;
        this.delivery = delivery;
        this.mailPool = mailPool;
        this.receivedDispatch = false;
        this.deliveryCounter = 0;
    }

    /**
     * This is called when a robot is assigned the mail items and ready to dispatch for the delivery
     */
    public void dispatch() {
	receivedDispatch = true;
    }

    /**
     * This is called on every time step
     * @throws ExcessiveDeliveryException if robot delivers more than the capacity of the tube without refilling
     * @throws Exception
     */
    public void operate() throws ExcessiveDeliveryException, Exception {
	switch(current_state) {
	
		/** This state is triggered when the robot is returning to the mail room after a delivery */
		case RETURNING:
			/** If its current position is at the mail room, then the robot should change state */
                if(current_floor == Building.MAILROOM_LOCATION){
                	if (tube != null) { // TODO: figure out if and how this can ever be true
                		//so far testing has indicated this never happens
                		tube.resetActivity();
                		mailPool.addItemToPools(tube);
                        System.out.printf("T: %3d > old addToPool [%s]%n", Clock.Time(), tube.toString());
                        tube = null;
                	}	
				/** Tell the sorter the robot is ready */
				mailPool.registerWaiting(this);
				changeState(RobotState.WAITING);
                } else {
			/** If the robot is not at the mail room floor yet, then move towards it! */
                    moveTowards(Building.MAILROOM_LOCATION);
			break;
                }
                
		case WAITING:
                /** If the StorageTube is ready and the Robot is waiting in the mailroom then start the delivery */
                if(!isEmpty() && receivedDispatch){
			receivedDispatch = false;
			deliveryCounter = 0; // reset delivery counter
			setDestination();
			changeState(RobotState.DELIVERING);
                }
                break;
                
		case DELIVERING:
			if(current_floor == destination_floor){ // If already here drop off either way
                    /** Delivery complete! */
				deliveryItem.recordMovement(current_floor - Building.MAILROOM_LOCATION); // for the return trip
				Accountant.recordDelivery(deliveryItem);
				/** report to the simulator */
                    delivery.deliver(deliveryItem);
                    deliveryItem = null;
                    deliveryCounter++;
                    if(deliveryCounter > 2){  // Implies a simulation bug
			throw new ExcessiveDeliveryException();
                    }
                    /** Check if want to return, i.e. if there is no item in the tube*/
                    if(tube == null){
			changeState(RobotState.RETURNING);
                    }
                    else{
                        /** If there is another item, set the robot's route to the location to deliver the item */
                        deliveryItem = tube;
                        tube = null;
                        setDestination();
                        changeState(RobotState.DELIVERING);
                    }
			} else {
				/** The robot is not at the destination yet, move towards it! */
			moveTowards(destination_floor);
			}
                break;
	}
    }

    /**
     * Records movement in both deliveryItem and tube
     */
    private void recordMovement(int movementCount) {
	assert(this.deliveryItem != null);

	deliveryItem.recordMovement(movementCount);
	if(tube != null) {
		tube.recordMovement(movementCount);
	}
    }

    /**
     * Sets the route for the robot
     */
    private void setDestination() {
        /** Set the destination floor */
        destination_floor = deliveryItem.getDestFloor();
    }

    /**
     * Generic function that moves the robot towards the destination
     * @param destination the floor towards which the robot is moving
     */
    private void moveTowards(int destination) {
        if(current_floor < destination){
            current_floor++;
        } else {
            current_floor--;
        }
        //only record movement if the robot has an item
        if(this.current_state == RobotState.DELIVERING) { 
        	recordMovement(1);
        }
    }

    private String getIdTube() {
	return String.format("%s(%1d)", this.id, (tube == null ? 0 : 1));
    }

    /**
     * Prints out the change in state
     * @param nextState the state to which the robot is transitioning
     */
    private void changeState(RobotState nextState){
	assert(!(deliveryItem == null && tube != null));
	if (current_state != nextState) {
            System.out.printf("T: %3d > %7s changed from %s to %s%n", Clock.Time(), getIdTube(), current_state, nextState);
	}
	current_state = nextState;
	if(nextState == RobotState.DELIVERING){
            System.out.printf("T: %3d > %7s-> [%s]%n", Clock.Time(), getIdTube(), deliveryItem.toString());
	}
    }

	public MailItem getTube() {
		return tube;
	}

	public boolean isEmpty() {
		return (deliveryItem == null && tube == null);
	}

	public void addToHand(MailItem mailItem) throws ItemTooHeavyException {
		assert(deliveryItem == null);
		deliveryItem = mailItem;
		if (deliveryItem.weight > INDIVIDUAL_MAX_WEIGHT) throw new ItemTooHeavyException();
	}

	public void addToTube(MailItem mailItem) throws ItemTooHeavyException {
		assert(tube == null);
		tube = mailItem;
		if (tube.weight > INDIVIDUAL_MAX_WEIGHT) throw new ItemTooHeavyException();
	}

}
