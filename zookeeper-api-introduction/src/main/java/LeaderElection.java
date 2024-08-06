import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class LeaderElection implements Watcher {
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181"; // 서버주소
    private static final int SESSION_TIMEOUT = 30000; // 세션 타임아웃 설정
    private static final String ELECTION_NAMESPACE = "/election";
    private ZooKeeper zooKeeper;   // 주키퍼 객체의 인스턴스 저장
    private String currentZnodeName;

    public static void main(String[] args) {
        LeaderElection leaderElection = new LeaderElection();
        try {
            leaderElection.connectToZookeeper();
            leaderElection.volunteerForLeadership(); // 리더선출을 위한 메소드1
            leaderElection.electLeader(); // 리더선출을 위한 메소드2
            leaderElection.run();
            leaderElection.close();
            System.out.println("Disconnected from Zookeeper, exiting application");
            System.out.flush(); // 표준 출력 버퍼를 강제로 비우기
        } catch (IOException | InterruptedException | KeeperException e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
            System.out.flush(); // 오류 메시지를 즉시 출력하기 위해 플러시 사용
        }
    }

    public void volunteerForLeadership() throws KeeperException, InterruptedException {
        String znodePrefix = ELECTION_NAMESPACE + "/c_";
        try {
            String znodeFullPath = zooKeeper.create(znodePrefix, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            // public String create(String path, byte[] data, List<ACL> acl, CreateMode createMode) throws KeeperException, InterruptedException
            // 기본 create 메소드의 인자는 이렇게 정의되어 있으며, 관리자가 필요한 항목으로 변경
            // EPHEMERAL_SEQUENTIAL ==> 주키퍼와 연결이 끊어지면 해당 Znode는 삭제할 것!
            System.out.println("Znode name " + znodeFullPath);
            System.out.flush(); 
            this.currentZnodeName = znodeFullPath.replace(ELECTION_NAMESPACE + "/", "");
        } catch (KeeperException | InterruptedException e) {
            System.err.println("Exception while volunteering for leadership: " + e.getMessage());
            throw e;
            /* catch 블록에서 예외를 다시 던지는 이유는 예외 발생 사실을 호출자에게 알리고, 상위 호출자가 예외를 처리할 수 있도록 하기 위함입니다. 이를 통해 프로그램의 흐름을 제어하고, 예외 상황에서 적절한 조치를 취할 수 있습니다. 예외를 다시 던짐으로써 예외의 원인을 정확히 전달하고, 디버깅과 문제 해결을 용이하게 할 수 있음 */
            /* throw e;를 사용한 경우, 예외가 발생했을 때 volunteerForLeadership 메서드를 호출한 상위 코드 블록에서 이 예외를 처리해야 합니다. = main 메소드에서 이 예외처리를 담당함*/
       }
    }

    public void electLeader() throws KeeperException, InterruptedException {
        try {
            List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);
            /* public List<String> getChildren(String path, boolean watch) throws KeeperException, InterruptedException
             * 
             * zooKeeper.getChildren 메서드는 주어진 ZNode(데이터 노드)의 자식 노드 목록을 반환
               public List<String> getChildren(String path, boolean watch) throws KeeperException, InterruptedException
               매개변수 path (String): 자식 노드를 가져올 ZNode의 경로, watch (boolean): true로 설정하면, 이 ZNode의 자식 노드 목록에 대한 변경사항을 감시하는 Watcher가 설정됩니다.
               반환 값은 자식 노드들의 이름을 담은 List<String> 객체를 반환합니다.
             */
            Collections.sort(children); // 가장 작은 znode를 찾기 위해 사전순으로 정렬!
            String smallestChild = children.get(0);
            if (smallestChild.equals(currentZnodeName)) {
                System.out.println("I am the leader");
                System.out.flush(); 
            } else {
                System.out.println("I am not the leader, " + smallestChild + " is the leader");
                System.out.flush(); 
            }
        } catch (KeeperException | InterruptedException e) {
            System.err.println("Exception while electing leader: " + e.getMessage());
            throw e;
        }
    }

    public void connectToZookeeper() throws IOException {
        zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
    } // public ZooKeeper(String connectString, int sessionTimeout, Watcher watcher)

    private void run() throws InterruptedException {
        synchronized (zooKeeper) {
            zooKeeper.wait();
        }
    }

    private void close() throws InterruptedException {
        zooKeeper.close();
    }

    @Override
    public void process(WatchedEvent event) {  /*  Watcher 인터페이스에 이미 타입변환이 정의됨 WatchedEvent는 ZooKeeper에서 발생하는 이벤트에 대한 정보를 담고 있는 클래스로,
                                                   이벤트의 타입, 상태, 경로 등의 정보를 포함합니다. */
        switch (event.getType()) {
            //  case None과 default로 나뉘는 건 어떤 기준?
            /*  EventType.None, EventType.NodeCreated, EventType.NodeDeleted,EventType.NodeDataChanged, EventType.NodeChildrenChanged
                None 대신 들어갈 수 있는 값들이며, default의 경우 예상치 못한 이벤트를 대표하여 사용됨 */
            case None:
                if (event.getState() == Event.KeeperState.SyncConnected) {
                    //KeeperState.SyncConnected, KeeperState.Disconnected, KeeperState.Expired, KeeperState.AuthFailed
                    System.out.println("Successfully connected to Zookeeper");
                    System.out.flush(); 
                } else {
                    synchronized (zooKeeper) {
                        System.out.println("Disconnected from Zookeeper event");
                        System.out.flush(); 
                        zooKeeper.notifyAll();
                    }
                }
                break;
            default:
            // EventType 열거형에 정의되지 않은 값이거나, 코드에서 명시적으로 처리하지 않은 모든 이벤트 타입이 될 수 있음
            /*  1. 미래에 추가된 새로운 이벤트 타입:

              ZooKeeper 라이브러리가 업데이트되어 새로운 이벤트 타입이 추가된 경우, 기존 코드는 이러한 새로운 이벤트 타입을 처리하지 못할 수 있습니다. 이러한 경우 default 절에서 처리하게 됩니다.
              예를 들어, 새로운 EventType.NodeMoved 이벤트가 추가될 수 있습니다.
               
                2. 코드에서 명시적으로 처리하지 않은 이벤트 타입:

             위에 언급된 이벤트 타입 외에 코드에서 특정 이벤트 타입을 처리하지 않았다면, 그 이벤트 타입은 default 절에서 처리됩니다.
             예를 들어, NodeDataChanged와 NodeChildrenChanged만 처리하는 코드를 작성한 경우, NodeCreated와 NodeDeleted 이벤트는 default 절에서 처리됩니다. */
                System.out.println("Unhandled event type: " + event.getType());
                System.out.flush(); // 콘솔에 즉시 출력합니다.
        }
    }
}
