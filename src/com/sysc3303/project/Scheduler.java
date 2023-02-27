/**
 * 
 */
package com.sysc3303.project;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * The Scheduler class which schedules the elevators and enables communication
 * between the Floor and Elevator subsystems
 * 
 * @author Group 9
 *
 */
public class Scheduler implements Runnable {
	private final Queue<FloorRequest> events; //request queue
	private final Queue<ElevatorResponse> responses; // response queue
	private SchedulerState state; //the state of the scheduler
	public static final int FLOOR_REQUEST_PORT = 4999; //Port for floor subsystem to send requests to
	public static final int ELEVATOR_REQUEST_PORT = 5555; //Port for elevators to send requests to
	public static final int ELEVATOR_RESPONSE_PORT = 5556; //Port for elevator to send response objects to
	public static final InetAddress ADDRESS = UDPUtil.getLocalHost();
	public final DatagramSocket responseSocket, elevatorRequestSocket, floorRequestSocket;

	/**
	 * Constructor; initializes all attributes with default values
	 */
	public Scheduler() {
		events = new ArrayDeque<>();
		responses = new ArrayDeque<>();
		state = new SchedulerReceivingState();
		elevatorRequestSocket = UDPUtil.createDatagramSocket(ELEVATOR_REQUEST_PORT);
		responseSocket = UDPUtil.createDatagramSocket(ELEVATOR_RESPONSE_PORT);
		floorRequestSocket = UDPUtil.createDatagramSocket(FLOOR_REQUEST_PORT);
	}
	
	/**
	 * Closes the sockets associated with the object
	 */
	public void closeSockets() {
		responseSocket.close();
		elevatorRequestSocket.close();
		floorRequestSocket.close();
	}

	/**
	 * Scheduler thread's run method
	 */
	@Override
	public void run() {
		System.out.println(Thread.currentThread().getName() + ": scheduler currently in state "  + state.toString());
		
		Thread requestsFromFloor = new Thread(() -> {
			while (true) {
				receiveRequestFromFloor();
			}
		}, "SchedulerThread-1");
		
		Thread requestsFromElevator = new Thread(() -> {
			while (true) {
				receiveRequestFromElevator();
			}
		}, "SchedulerThread-2");
		
		Thread responsesFromElevator = new Thread(() -> {
			while (true) {
				receiveResponseFromElevator();
			}
		}, "SchedulerThread-3");
		
		Thread sendToFloor = new Thread(() -> {
			while (true) {
				sendResponseToFloor();
			}
		}, "SchedulerThread-4");
		
		//start scheduler threads
		requestsFromFloor.start();
		requestsFromElevator.start();
		responsesFromElevator.start();
		sendToFloor.start();
	}
	
	/**
	 * Receives FloorRequest from Floor subsystem and adds it to the event queue
	 */
	private void receiveRequestFromFloor() {
		DatagramPacket receivePacket = new DatagramPacket(new byte[UDPUtil.RECEIVE_PACKET_LENGTH], UDPUtil.RECEIVE_PACKET_LENGTH);
		UDPUtil.receivePacket(floorRequestSocket, receivePacket);
		FloorRequest request = (FloorRequest) UDPUtil.convertFromBytes(receivePacket.getData(), receivePacket.getLength());
		addFloorRequest(request);
	}
	
	/**
	 * Sends the response message to the Floor subsystem
	 */
	private void sendResponseToFloor() {
		ElevatorResponse response = getElevatorResponse();
		DatagramSocket socket = UDPUtil.createDatagramSocket();
		byte[] data = response.getMessage().getBytes();
		DatagramPacket packet = new DatagramPacket(data, data.length, Floor.ADDRESS, Floor.PORT);
		UDPUtil.sendPacket(socket, packet);
		socket.close();
		state.handleResponseProcessed(this); // response has been received and processed
	}
	
	/**
	 * Receives a response object from an elevator and adds it to the response queue
	 */
	private void receiveResponseFromElevator() {
		DatagramPacket receivePacket = new DatagramPacket(new byte[UDPUtil.RECEIVE_PACKET_LENGTH], UDPUtil.RECEIVE_PACKET_LENGTH);
		UDPUtil.receivePacket(responseSocket, receivePacket);
		ElevatorResponse response = (ElevatorResponse) UDPUtil.convertFromBytes(receivePacket.getData(), receivePacket.getLength());
		addElevatorResponse(response);
		
	}
	
	/**
	 * Receives a request from an elevator and sends it back a floor request
	 */
	private void receiveRequestFromElevator() {
		DatagramPacket receivePacket = new DatagramPacket(new byte[UDPUtil.RECEIVE_PACKET_LENGTH], UDPUtil.RECEIVE_PACKET_LENGTH);
		UDPUtil.receivePacket(elevatorRequestSocket, receivePacket);
		FloorRequest request = getNextRequest();
		byte[] data = UDPUtil.convertToBytes(request);
		DatagramPacket sendPacket = new DatagramPacket(data, data.length, receivePacket.getAddress(), receivePacket.getPort());
		DatagramSocket socket = UDPUtil.createDatagramSocket(); 
		UDPUtil.sendPacket(socket, sendPacket);
		socket.close();
	}
	
	/**
	 * Gets the state of the Scheduler
	 * 
	 * @return the state of the Scheduler
	 */
	public synchronized SchedulerState getState() {
		return state;
	}
	
	/**
	 * Sets the state of the Scheduler
	 * 
	 * @param state the new state of the Scheduler
	 */
	public synchronized void setState(SchedulerState state) {
		this.state = state;
	}

	/**
	 * Adds a FloorRequest to the request queue
	 * 
	 * @param floorRequest The FloorRequest to add
	 */
	public synchronized void addFloorRequest(FloorRequest floorRequest) {
		if (floorRequest == null) {
			throw new IllegalArgumentException("The FloorRequest object cannot be null");
		}
		events.add(floorRequest);
		notifyAll(); // notifies threads that a floor request is available
	}

	/**
	 * Gets and removes the FloorRequest at the head of the queue
	 * 
	 * @return The next FloorRequest in the queue
	 */
	public synchronized FloorRequest getNextRequest() {
		while (events.isEmpty()) {
			try {
				wait(); // wait until a request is available
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		return events.remove();
	}

	/**
	 * Adds an ElevatorResponse to the response queue
	 * 
	 * @param response The ElevatorResponse to add
	 */
	public synchronized void addElevatorResponse(ElevatorResponse response) {
		if (response == null) {
			throw new IllegalArgumentException("The ElevatorResponse object cannot be null");
		}
		responses.add(response);
		notifyAll(); // notifies scheduler that response can be sent to Floor thread
		state.handleResponseReceived(this); //handle response received event
	}

	/**
	 * Gets and removes the ElevatorResponse from the head of the response queue
	 * 
	 * @return elevatorResponse The next ElevatorResponse object
	 */
	public synchronized ElevatorResponse getElevatorResponse() {
		while (responses.isEmpty()) {
			try {
				wait(); // waits for response to be available
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		return responses.remove();
	}
	
	/**
	 * @return true if there is at least one response in the response queue, and false otherwise
	 */
	public synchronized boolean isResponseInQueue() {
		return !responses.isEmpty();
	}
	
	/**
	 * Entry point to Scheduler subsystem
	 */
	public static void main(String[] args) {
		Scheduler scheduler = new Scheduler();
		Thread thread = new Thread(scheduler, "SchedulerSubsystem");
		thread.start();
	}

}
