package com.sysc3303.project.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sysc3303.project.ElevatorEvent;
import com.sysc3303.project.Floor;
import com.sysc3303.project.FloorRequest;
import com.sysc3303.project.Scheduler;
import com.sysc3303.project.Time;
import com.sysc3303.project.ElevatorEvent.Direction;

/**
 * @author Group 9
 *
 */
class FloorRequestTest {
	private static FloorRequest floorRequest;
	private static Floor floor = new Floor(new Scheduler());
	private static ElevatorEvent elevatorEvent = new ElevatorEvent(new Time("1", "1", "1", "1"), 3, ElevatorEvent.Direction.UP, 4 );

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		floorRequest = new FloorRequest(floor, elevatorEvent);
	}

	@Test
	void gettersTest() {
		assertEquals(floor, floorRequest.getFloor());
		assertEquals(elevatorEvent, floorRequest.getElevatorEvent());
	}

}