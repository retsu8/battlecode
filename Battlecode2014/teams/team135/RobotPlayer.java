package team135;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.Robot;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.TerrainTile;

public class RobotPlayer {
	static Random rand;
	static Direction[] directions;

	static int maxHQ = 8;

	static int pastrChannel = 57;
	static int attackChannel = 58;
	static int HQSpaces = 8;
	static boolean signaledAtHQ = false;

	static boolean followingWall = false;
	static int turnsRemaining = 3;
	static int numRobots = 0;

	static boolean awall = false;

	public static void run(RobotController rc) {
		rand = new Random();
		directions = new Direction[] { Direction.NORTH, Direction.NORTH_EAST,
				Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH,
				Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST };
		pastrChannel = rand.nextInt(GameConstants.BROADCAST_MAX_CHANNELS);
		int defenseReady = 8;
		while (true) {
			if (rc.getType() == RobotType.HQ) {
				int cnt = 0;
				for (Direction d: directions)
				{
					if (rc.canMove(d))
						cnt++;	
				}
				try{
				rc.broadcast(HQSpaces, cnt);
				}
				catch (Exception e)
				{
					
				}
				try {
					// Check if a robot is spawnable and spawn one if it is
					if (rc.isActive()) {
						Robot[] nearbyEnemies = rc.senseNearbyGameObjects(
								Robot.class, 10, rc.getTeam().opponent());
						if (nearbyEnemies.length > 0
								&& rc.senseRobotInfo(nearbyEnemies[0]).type != RobotType.HQ) {
							RobotInfo robotInfo = rc
									.senseRobotInfo(nearbyEnemies[0]);
							rc.attackSquare(robotInfo.location);
						}
					}
					if (rc.isActive() && rc.senseRobotCount() < 25) {
						int r = rand.nextInt(8);
						Direction spawnDir = directions[r];
						if (rc.senseObjectAtLocation(rc.getLocation().add(
								spawnDir)) == null) {
							rc.spawn(spawnDir);
						}
					}
				} catch (Exception e) {
					System.out.println("HQ Exception");
				}
			}

			if (rc.getType() == RobotType.SOLDIER) {
				try {
					if (rc.getLocation().distanceSquaredTo(rc.senseHQLocation()) < 10)
						numRobots++;
					if (rc.isActive()) {
						if (rc.readBroadcast(HQSpaces) != 0 && numRobots >= rc.readBroadcast(HQSpaces))
						{
							if (rc.readBroadcast(attackChannel) < maxHQ && !awall)
								attackBot(rc);
							else if (rc.senseBroadcastingRobotLocations(rc
									.getTeam()).length > 0)
								guardBot(rc);
							else
								pastrBot(rc);
						}
						else
						{
							Robot[] nearbyEnemies = rc.senseNearbyGameObjects(
									Robot.class, 10, rc.getTeam().opponent());
							if (nearbyEnemies.length > 0
									&& rc.senseRobotInfo(nearbyEnemies[0]).type != RobotType.HQ) {
								RobotInfo robotInfo = rc.senseRobotInfo(nearbyEnemies[0]);
								rc.attackSquare(robotInfo.location);
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			if (rc.getType() == RobotType.PASTR) {
				try {
					if (rc.isActive()) {
						Robot[] guards = rc.senseNearbyGameObjects(Robot.class,
								10, rc.getTeam());
						boolean guarded = false;
						for (Robot r : guards)
							if (rc.senseRobotInfo(r).type == RobotType.SOLDIER)
								guarded = true;
						if (!guarded)
							rc.broadcast(pastrChannel, rc.getRobot().getID());
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			rc.yield();
		}
	}

	public static void attackBot(RobotController rc) throws GameActionException {
		if (followingWall) {
			followWall(rc);
			return;
		}
		Robot[] nearbyEnemies = rc.senseNearbyGameObjects(Robot.class, 10, rc
				.getTeam().opponent());
		MapLocation target = getTarget(rc);
		if (rc.getLocation().distanceSquaredTo(target) < 200
				&& nearbyEnemies.length > 0
				&& rc.senseRobotInfo(nearbyEnemies[0]).type != RobotType.HQ)
			target = rc.senseRobotInfo(nearbyEnemies[0]).location;
		if (rc.getLocation().distanceSquaredTo(target) < 200
				&& nearbyEnemies.length > 1
				&& rc.senseRobotInfo(nearbyEnemies[1]).type != RobotType.HQ)
			target = rc.senseRobotInfo(nearbyEnemies[1]).location;
		Direction toOpponent = rc.getLocation().directionTo(target);
		if (rc.getLocation().distanceSquaredTo(target) < 20 && !signaledAtHQ) {
			signaledAtHQ = true;
			rc.broadcast(attackChannel, rc.readBroadcast(attackChannel) + 1);
		}

		if (nearbyEnemies.length > 0
				&& rc.senseRobotInfo(nearbyEnemies[0]).type != RobotType.HQ) {
			RobotInfo robotInfo = rc.senseRobotInfo(nearbyEnemies[0]);
			rc.attackSquare(robotInfo.location);
			return;
		}
		int i = 0;
		if (rc.canMove(toOpponent)) {
			rc.move(toOpponent);
			return;
		} else if (rc.canMove(toOpponent.rotateLeft())) {
			rc.move(toOpponent.rotateLeft());
			return;
		} else if (rc.canMove(toOpponent.rotateRight())) {
			rc.move(toOpponent.rotateRight());
			return;
		} else if (rc.senseTerrainTile(rc.getLocation().add(toOpponent)) == TerrainTile.VOID) {
			turnsRemaining = 3;
			followWall(rc);
			return;
		}

		if (rc.getHealth() < 10) {
			rc.broadcast(attackChannel, rc.readBroadcast(attackChannel) - 1);
			rc.selfDestruct();
		}
	}

	public static void guardBot(RobotController rc) throws GameActionException {
		if (signaledAtHQ) {
			rc.broadcast(attackChannel, rc.readBroadcast(attackChannel) - 1);
			signaledAtHQ = false;
			awall = true;
		}
		Robot[] nearby = rc
				.senseNearbyGameObjects(Robot.class, 4, rc.getTeam());
		boolean atPost = false;
		for (Robot r : nearby)
			if (rc.senseRobotInfo(r).type == RobotType.PASTR)
				atPost = true;
		if (!atPost) {
			MapLocation[] broadcasters = rc.senseBroadcastingRobotLocations(rc
					.getTeam());
			if (broadcasters.length == 0)
				return;
			Direction toBroadcaster = rc.getLocation().directionTo(
					broadcasters[0]);

			int i = 0;
			for (Direction d = toBroadcaster; i < 8; d = d.rotateLeft()) {
				if (rc.canMove(d)) {
					rc.move(d);
					return;
				}
				i++;
			}
		} else {
			Robot[] nearbyEnemies = rc.senseNearbyGameObjects(Robot.class, 10,
					rc.getTeam().opponent());
			if (nearbyEnemies.length > 0
					&& rc.senseRobotInfo(nearbyEnemies[0]).type != RobotType.HQ) {
				RobotInfo robotInfo = rc.senseRobotInfo(nearbyEnemies[0]);
				rc.attackSquare(robotInfo.location);
			}
		}

	}
	
	public static void pastrBot(RobotController rc) throws GameActionException {
		if (signaledAtHQ) {
			rc.broadcast(attackChannel, rc.readBroadcast(attackChannel) - 1);
			signaledAtHQ = false;
			awall = true;
		}
		if (rand.nextInt(50) < 1
				&& rc.senseCowsAtLocation(rc.getLocation()) > 100)
			rc.construct(RobotType.PASTR);
		double[] cows = new double[8];
		for (int i = 0; i < 8; i++)
			cows[i] = rc.senseCowsAtLocation(rc.getLocation()
					.add(directions[i]));
		int index = maxIndex(cows);
		if (index < 0) {
			for (Direction d : directions) {
				if (rc.canMove(d)) {
					rc.sneak(d);
					return;
				}
			}
			return;
		}
		if (rc.senseCowsAtLocation(rc.getLocation()) >= Math.max(cows[index],
				100))
			rc.construct(RobotType.PASTR);
		else if (rc.canMove(directions[index]))
			rc.sneak(directions[index]);
		else {
			Direction d = directions[rand.nextInt(8)];
			if (rc.canMove(d))
				rc.move(d);
		}
	}

	public static void followWall(RobotController rc)
			throws GameActionException {
		Robot[] nearbyEnemies = rc.senseNearbyGameObjects(Robot.class, 10, rc
				.getTeam().opponent());
		if (nearbyEnemies.length > 0
				&& rc.senseRobotInfo(nearbyEnemies[0]).type != RobotType.HQ) {
			RobotInfo robotInfo = rc.senseRobotInfo(nearbyEnemies[0]);
			rc.attackSquare(robotInfo.location);
			return;
		}

		followingWall = true;
		boolean[] isVoid = new boolean[8];
		int numWalls = 0;
		for (int i = 0; i < 8; i++)
			if (rc.senseTerrainTile(rc.getLocation().add(directions[i])) ==
				TerrainTile.VOID || rc.senseObjectAtLocation(rc.getLocation().
				add(directions[i])) != null) {
				isVoid[i] = true;
				numWalls++;
			}

		if (numWalls == 0) {
			followingWall = false;
			return;
		} else {

			for (int i = 0; i < 8; i += 2)
				if (isVoid[i]) {
					if (rc.canMove(directions[(i + 6) % 8])) {
						rc.move(directions[(i + 6) % 8]);
						return;
					}
				}
			for (int i = 1; i < 8; i += 2)
				if (isVoid[i]) {
					if (rc.canMove(directions[(i + 7) % 8])) {
						if (--turnsRemaining == 0)
							followingWall = false;
						rc.move(directions[(i + 7) % 8]);

						return;
					}
				}

			for (int i = 0; i < 8; i += 2)
				if (isVoid[i]) {
					if (rc.canMove(directions[(i + 2) % 8])) {
						rc.move(directions[(i + 2) % 8]);
						return;
					}
				}
			for (int i = 1; i < 8; i += 2)
				if (isVoid[i]) {
					if (rc.canMove(directions[(i + 1) % 8])) {
						if (--turnsRemaining == 0)
							followingWall = false;
						rc.move(directions[(i + 1) % 8]);

						return;
					}
				}
		}
	}

	public static MapLocation getTarget(RobotController rc)
			throws GameActionException {
		final MapLocation HQ = rc.senseEnemyHQLocation();
		MapLocation[] pastrs = rc.sensePastrLocations(rc.getTeam().opponent());
		if (pastrs.length == 0)
			return HQ;
		Arrays.sort(pastrs, new Comparator<MapLocation>() {
			public int compare(MapLocation a, MapLocation b) {
				return a.distanceSquaredTo(HQ) - b.distanceSquaredTo(HQ);
			}
		});
		return pastrs[0];
	}

	public static int maxIndex(double[] xs) {
		int index = -1;
		for (int i = 0; i < xs.length; i++)
			if ((index == -1 && xs[i] > xs[0])
					|| (index != -1 && xs[i] > xs[index]))
				index = i;
		return index;
	}
}
