package cluster.sharding;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Runner {
    public static void main(String[] args) {
        List<ActorSystem> actorSystems;

        if (args.length == 0) {
            actorSystems = startupClusterNodes(Arrays.asList("2551", "2552", "0"));
        } else {
            actorSystems = startupClusterNodes(Arrays.asList(args));
        }

        writef("Hit enter to stop%n");
        readLine();

        for (ActorSystem actorSystem : actorSystems) {
            Cluster cluster = Cluster.get(actorSystem);
            cluster.leave(cluster.selfAddress());
        }
    }

    private static List<ActorSystem> startupClusterNodes(List<String> ports) {
        writef("Start cluster on port(s) %s%n", ports);
        List<ActorSystem> actorSystems = new ArrayList<>();

        for (String port : ports) {
            ActorSystem actorSystem = ActorSystem.create("sharding", setupClusterNodeConfig(port));

            actorSystem.actorOf(ClusterListenerActor.props(), "clusterListener");

            ActorRef shardingRegion = setupClusterSharding(actorSystem);

            actorSystem.actorOf(EntityCommandActor.props(shardingRegion), "entityCommand");
            actorSystem.actorOf(EntityQueryActor.props(shardingRegion), "entityQuery");

            actorSystems.add(actorSystem);
        }
        return actorSystems;
    }

    private static Config setupClusterNodeConfig(String port) {
        return ConfigFactory.parseString(
                String.format("akka.remote.netty.tcp.port=%s%n", port) +
                        String.format("akka.remote.artery.canonical.port=%s%n", port))
                .withFallback(ConfigFactory.load()
                );
    }

    private static ActorRef setupClusterSharding(ActorSystem actorSystem) {
        ClusterShardingSettings settings = ClusterShardingSettings.create(actorSystem);
        return ClusterSharding.get(actorSystem).start(
                "entity",
                EntityActor.props(),
                settings,
                EntityMessage.messageExtractor()
        );
    }

    private static void writef(String format, Object... args) {
        System.out.printf(format, args);
    }

    private static void readLine() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
