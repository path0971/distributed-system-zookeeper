import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class LeaderElection implements Watcher {
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 30000;
    private static final String ELECTION_NAMESPACE = "/election";
    private ZooKeeper zooKeeper;
    private String currentZnodeName;

    public static void main(String[] args) {
        LeaderElection leaderElection = new LeaderElection();
        try {
            leaderElection.connectToZookeeper();
            leaderElection.volunteerForLeadership();
            leaderElection.electLeader();
            leaderElection.run();
            leaderElection.close();
            System.out.println("Disconnected from Zookeeper, exiting application");
            System.out.flush(); // 표준 출력 버퍼를 강제로 비웁니다.
        } catch (IOException | InterruptedException | KeeperException e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
            System.out.flush(); // 오류 메시지를 즉시 출력하기 위해 플러시합니다.
        }
    }

    public void volunteerForLeadership() throws KeeperException, InterruptedException {
        String znodePrefix = ELECTION_NAMESPACE + "/c_";
        try {
            String znodeFullPath = zooKeeper.create(znodePrefix, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            System.out.println("Znode name " + znodeFullPath);
            System.out.flush(); // 콘솔에 즉시 출력합니다.
            this.currentZnodeName = znodeFullPath.replace(ELECTION_NAMESPACE + "/", "");
        } catch (KeeperException | InterruptedException e) {
            System.err.println("Exception while volunteering for leadership: " + e.getMessage());
            throw e;
        }
    }

    public void electLeader() throws KeeperException, InterruptedException {
        try {
            List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);
            Collections.sort(children);
            String smallestChild = children.get(0);
            if (smallestChild.equals(currentZnodeName)) {
                System.out.println("I am the leader");
                System.out.flush(); // 콘솔에 즉시 출력합니다.
            } else {
                System.out.println("I am not the leader, " + smallestChild + " is the leader");
                System.out.flush(); // 콘솔에 즉시 출력합니다.
            }
        } catch (KeeperException | InterruptedException e) {
            System.err.println("Exception while electing leader: " + e.getMessage());
            throw e;
        }
    }

    public void connectToZookeeper() throws IOException {
        zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
    }

    private void run() throws InterruptedException {
        synchronized (zooKeeper) {
            zooKeeper.wait();
        }
    }

    private void close() throws InterruptedException {
        zooKeeper.close();
    }

    @Override
    public void process(WatchedEvent event) {
        switch (event.getType()) {
            case None:
                if (event.getState() == Event.KeeperState.SyncConnected) {
                    System.out.println("Successfully connected to Zookeeper");
                    System.out.flush(); // 콘솔에 즉시 출력합니다.
                } else {
                    synchronized (zooKeeper) {
                        System.out.println("Disconnected from Zookeeper event");
                        System.out.flush(); // 콘솔에 즉시 출력합니다.
                        zooKeeper.notifyAll();
                    }
                }
                break;
            default:
                System.out.println("Unhandled event type: " + event.getType());
                System.out.flush(); // 콘솔에 즉시 출력합니다.
        }
    }
}
