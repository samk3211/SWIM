/*
* Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
* 2009 Royal Institute of Technology (KTH)
*
* GVoD is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
package se.kth.swim.simulation;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.javatuples.Pair;
import se.kth.swim.AggregatorComp;
import se.kth.swim.HostComp;
import se.kth.swim.croupier.CroupierConfig;
import se.sics.p2ptoolbox.simulator.cmd.OperationCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.ChangeNetworkModelCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.SimulationResult;
import se.sics.p2ptoolbox.simulator.cmd.impl.StartAggregatorCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.StartNodeCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.KillNodeCmd;
import se.sics.p2ptoolbox.simulator.core.network.NetworkModel;
import se.sics.p2ptoolbox.simulator.core.network.impl.DeadLinkNetworkModel;
import se.sics.p2ptoolbox.simulator.core.network.impl.DisconnectedNodesNetworkModel;
import se.sics.p2ptoolbox.simulator.core.network.impl.UniformRandomModel;
import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation1;
import se.sics.p2ptoolbox.simulator.dsl.distribution.ConstantDistribution;
import se.sics.p2ptoolbox.simulator.dsl.distribution.extra.GenIntSequentialDistribution;
import se.sics.p2ptoolbox.util.network.NatType;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;


public class SwimScenario {
    
    
    private static long seed;
    private static InetAddress localHost;
    private static CroupierConfig croupierConfig = new CroupierConfig(20, 5, 2000, 1000);
    
    
    static {
        try {
            localHost = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    //Make sure that your dead link set reflect the nodes in your system
    private static final Map<Integer, Set<Pair<Integer, Integer>>> deadLinksSets = new HashMap<Integer, Set<Pair<Integer, Integer>>>();
    
    static {
        Set<Pair<Integer, Integer>> deadLinks;
        
        deadLinks = new HashSet<Pair<Integer, Integer>>();
        deadLinks.add(Pair.with(10, 12));
        deadLinks.add(Pair.with(12, 10));
        deadLinksSets.put(1, deadLinks);
        
        deadLinks = new HashSet<Pair<Integer, Integer>>();
        deadLinks.add(Pair.with(10, 12));
        deadLinks.add(Pair.with(12, 10));
        deadLinks.add(Pair.with(13, 10));
        deadLinksSets.put(2, deadLinks);
    }
    
    //Make sure disconnected nodes reflect your nodes in the system
    private static final Map<Integer, Set<Integer>> disconnectedNodesSets = new HashMap<Integer, Set<Integer>>();
    
    static {
        Set<Integer> disconnectedNodes;
        
        disconnectedNodes = new HashSet<Integer>();
        disconnectedNodes.add(10);
        disconnectedNodesSets.put(1, disconnectedNodes);
        
        disconnectedNodes = new HashSet<Integer>();
        disconnectedNodes.add(10);
        disconnectedNodes.add(12);
        disconnectedNodesSets.put(2, disconnectedNodes);
    }
    
    static Operation1<StartAggregatorCmd, Integer> startAggregatorOp = new Operation1<StartAggregatorCmd, Integer>() {
        
        @Override
        public StartAggregatorCmd generate(final Integer nodeId) {
            return new StartAggregatorCmd<AggregatorComp, NatedAddress>() {
                private NatedAddress aggregatorAddress;
                
                @Override
                public Class getNodeComponentDefinition() {
                    return AggregatorComp.class;
                }
                
                @Override
                public AggregatorComp.AggregatorInit getNodeComponentInit() {
                    aggregatorAddress = new BasicNatedAddress(new BasicAddress(localHost, 23456, nodeId));
                    return new AggregatorComp.AggregatorInit(aggregatorAddress);
                }
                
                @Override
                public NatedAddress getAddress() {
                    return aggregatorAddress;
                }
                
            };
        }
    };
    
    static Operation1<StartNodeCmd, Integer> startNodeOp = new Operation1<StartNodeCmd, Integer>() {
        
        @Override
        public StartNodeCmd generate(final Integer nodeId) {
            return new StartNodeCmd<HostComp, NatedAddress>() {
                private NatedAddress nodeAddress;
                
                @Override
                public Class getNodeComponentDefinition() {
                    return HostComp.class;
                }
                
                @Override
                public HostComp.HostInit getNodeComponentInit(NatedAddress aggregatorServer, Set<NatedAddress> bootstrapNodes) {
                    
                    if (nodeId % 2 == 0) {
                        //open address
                        nodeAddress = new BasicNatedAddress(new BasicAddress(localHost, 12345, nodeId));
                    } else {
                        //nated address
                        nodeAddress = new BasicNatedAddress(new BasicAddress(localHost, 12345, nodeId), NatType.NAT, bootstrapNodes);
                    }
                    /**
                     * we don't want all nodes to start their pseudo random
                     * generators with same seed else they might behave the same
                     */
                    long nodeSeed = seed + nodeId;
                    System.err.println("Starting node " + nodeId);
                    return new HostComp.HostInit(nodeAddress, bootstrapNodes, aggregatorServer, nodeSeed, croupierConfig);
                }
                
                @Override
                public Integer getNodeId() {
                    return nodeId;
                }
                
                @Override
                public NatedAddress getAddress() {
                    return nodeAddress;
                }
                
                @Override
                public int bootstrapSize() {
                    return 5;
                }
                
            };
        }
    };
    
    static Operation1<KillNodeCmd, Integer> killNodeOp = new Operation1<KillNodeCmd, Integer>() {
        
        public KillNodeCmd generate(final Integer nodeId) {
            
            System.err.println("Killing node " + nodeId);
            return new KillNodeCmd() {
                public Integer getNodeId() {
                    return nodeId;
                }
            };
        }
        
    };
    
    //Usable NetworkModels:
    //1. UniformRandomModel
    //parameters: minimum link latency, maximum link latency
    //by default Simulator starts with UniformRandomModel(50, 500), so minimum link delay:50ms, maximum link delay:500ms
    //2. DeadLinkNetworkModel
    //composite network model that can be built on any other network model
    //parameters: network model, set of dead links (directed links)
    //Pair<1,2> means if node 1 will try to send a message to node 2, the simulator will drop this message, since this is a dead link
    //3. DisconnectedNodesNetworkModel
    //composite network model that can be built on any other network model
    //parameters: network model, set of disconnected nodes
    //a disconnected node will not be able to send or receive messages
    static Operation1<ChangeNetworkModelCmd, Integer> disconnectedNodesNMOp = new Operation1<ChangeNetworkModelCmd, Integer>() {
        
        @Override
        public ChangeNetworkModelCmd generate(Integer setIndex) {
            NetworkModel baseNetworkModel = new UniformRandomModel(50, 500);
            NetworkModel compositeNetworkModel = new DisconnectedNodesNetworkModel(setIndex, baseNetworkModel, disconnectedNodesSets.get(setIndex));
            return new ChangeNetworkModelCmd(compositeNetworkModel);
        }
    };
    
    static Operation1<ChangeNetworkModelCmd, Integer> deadLinksNMOp = new Operation1<ChangeNetworkModelCmd, Integer>() {
        
        @Override
        public ChangeNetworkModelCmd generate(Integer setIndex) {
            NetworkModel baseNetworkModel = new UniformRandomModel(50, 500);
            NetworkModel compositeNetworkModel = new DeadLinkNetworkModel(setIndex, baseNetworkModel, deadLinksSets.get(setIndex));
            return new ChangeNetworkModelCmd(compositeNetworkModel);
        }
    };
    
    static Operation<SimulationResult> simulationResult = new Operation<SimulationResult>() {
        
        public SimulationResult generate() {
            return new SimulationResult() {
                
                @Override
                public void setSimulationResult(OperationCmd.ValidationException failureCause) {
                    SwimSimulationResult.failureCause = failureCause;
                }
            };
        }
    };
    
    //Operations require Distributions as parameters
    //1.ConstantDistribution - this will provide same parameter no matter how many times it is called
    //2.BasicIntSequentialDistribution - on each call it gives the next int. Works more or less like a counter
    //3.GenIntSequentialDistribution - give it a vector. It will draw elements from it on each call.
    //Once out of elements it will give null.
    //So be carefull for null pointer exception if you draw more times than elements
    //check se.sics.p2ptoolbox.simulator.dsl.distribution for more distributions
    //you can implement your own - by extending Distribution
    
    public static SimulationScenario simple10(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        //Change here your scenario parameters
        final int starting = 10;
        final int killing = 1; //not used
        final int reviving = 1;  //not used
        final boolean newnodes = false;
        
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                fetchSimulationResult.startAfterTerminationOf(10000000, startPeers);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario simple50(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        
        //Change here your scenario parameters
        final int starting = 50;
        final int killing = 1; //not used
        final int reviving = 1;  //not used
        final boolean newnodes = false;
        
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                
                
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                fetchSimulationResult.startAfterTerminationOf(10000000, startPeers);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario simple100(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        
        //Change here your scenario parameters
        final int starting = 100;
        final int killing = 1; //not used
        final int reviving = 1;  //not used
        final boolean newnodes = false;
        
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                fetchSimulationResult.startAfterTerminationOf(10000000, startPeers);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario simple500(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        
        //Change here your scenario parameters
        final int starting = 500;
        final int killing = 1; //not used
        final int reviving = 1;  //not used
        final boolean newnodes = false;
        
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                fetchSimulationResult.startAfterTerminationOf(1000000, startPeers);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario simpleDead100(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        
        //Change here your scenario parameters
        final int starting = 100;
        final int killing = 20;
        final int reviving = 0; //not used
        final boolean newnodes = false;
        
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(30000, startPeers);
                fetchSimulationResult.startAfterTerminationOf(1000000, startPeers);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario simpleDeadRevive100(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        
        //Change here your scenario parameters
        final int starting = 100;
        final int killing = 20;
        final int reviving = 20;
        final boolean newnodes = false;
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, killNodeOp, new ConstantDistribution(Integer.class, 20));
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess startDeadPeer = new SimulationScenario.StochasticProcess() {
                    {
                        int N = reviving;
                        int C;
                        
                        if(newnodes){
                            C = starting*2;
                        } else {
                            C = 0;
                        }
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = C+1; i < C+N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, startNodeOp, new GenIntSequentialDistribution(new Integer[]{20}));
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(5000, startPeers);
                startDeadPeer.startAfterTerminationOf(1000, killPeers);
                fetchSimulationResult.startAfterTerminationOf(100000, startDeadPeer);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario pbSize5(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        
        //Change here your scenario parameters
        final int starting = 100;
        final int killing = 20;
        final int reviving = 20;
        final boolean newnodes = false;
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, killNodeOp, new ConstantDistribution(Integer.class, 20));
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess startDeadPeer = new SimulationScenario.StochasticProcess() {
                    {
                        int N = reviving;
                        int C;
                        
                        if(newnodes){
                            C = starting*2;
                        } else {
                            C = 0;
                        }
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = C+1; i < C+N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, startNodeOp, new GenIntSequentialDistribution(new Integer[]{20}));
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(5000, startPeers);
                startDeadPeer.startAfterTerminationOf(1000, killPeers);
                fetchSimulationResult.startAfterTerminationOf(100000, startDeadPeer);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario pbSize10(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        
        //Change here your scenario parameters
        final int starting = 100;
        final int killing = 20;
        final int reviving = 20;
        final boolean newnodes = false;
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, killNodeOp, new ConstantDistribution(Integer.class, 20));
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess startDeadPeer = new SimulationScenario.StochasticProcess() {
                    {
                        int N = reviving;
                        int C;
                        
                        if(newnodes){
                            C = starting*2;
                        } else {
                            C = 0;
                        }
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = C+1; i < C+N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, startNodeOp, new GenIntSequentialDistribution(new Integer[]{20}));
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(5000, startPeers);
                startDeadPeer.startAfterTerminationOf(1000, killPeers);
                fetchSimulationResult.startAfterTerminationOf(100000, startDeadPeer);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario pbSize25(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        
        //Change here your scenario parameters
        final int starting = 100;
        final int killing = 20;
        final int reviving = 20;
        final boolean newnodes = false;
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, killNodeOp, new ConstantDistribution(Integer.class, 20));
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess startDeadPeer = new SimulationScenario.StochasticProcess() {
                    {
                        int N = reviving;
                        int C;
                        
                        if(newnodes){
                            C = starting*2;
                        } else {
                            C = 0;
                        }
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = C+1; i < C+N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, startNodeOp, new GenIntSequentialDistribution(new Integer[]{20}));
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(5000, startPeers);
                startDeadPeer.startAfterTerminationOf(1000, killPeers);
                fetchSimulationResult.startAfterTerminationOf(100000, startDeadPeer);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario pbSize100(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        
        //Change here your scenario parameters
        final int starting = 100;
        final int killing = 20;
        final int reviving = 20;
        final boolean newnodes = false;
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, killNodeOp, new ConstantDistribution(Integer.class, 20));
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess startDeadPeer = new SimulationScenario.StochasticProcess() {
                    {
                        int N = reviving;
                        int C;
                        
                        if(newnodes){
                            C = starting*2;
                        } else {
                            C = 0;
                        }
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = C+1; i < C+N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, startNodeOp, new GenIntSequentialDistribution(new Integer[]{20}));
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(5000, startPeers);
                startDeadPeer.startAfterTerminationOf(1000, killPeers);
                fetchSimulationResult.startAfterTerminationOf(100000, startDeadPeer);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario natedBoot(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        
        //Change here your scenario parameters
        final int starting = 100;
        final int killing = 10;
        final int reviving = 20;
        final boolean newnodes = false;
        
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 2; i < N+2; i++){
                            lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 2; i < N+2; i++){
                            lista[t++] = i;
                        }
                        
                        lista[0]=10;
                        lista[1]=22;
                        lista[2]=42;
                        lista[3]=52;
                        lista[4]=28;
                        lista[5]=88;
                        lista[6]=43;
                        lista[7]=32;
                        lista[8]=66;
                        lista[9]=21;
                        
                        
                        //raise(1, killNodeOp, new ConstantDistribution(Integer.class, 20));
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess startDeadPeer = new SimulationScenario.StochasticProcess() {
                    {
                        int N = reviving;
                        int C;
                        
                        if(newnodes){
                            C = starting*2;
                        } else {
                            C = 0;
                        }
                        
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = C+1; i < C+N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, startNodeOp, new GenIntSequentialDistribution(new Integer[]{20}));
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess killPeers2 = new SimulationScenario.StochasticProcess() {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        //raise(1, killNodeOp, new ConstantDistribution(Integer.class, 20));
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess startDeadPeer2 = new SimulationScenario.StochasticProcess() {
                    {
                        int N = reviving;
                        int C;
                        
                        if(newnodes){
                            C = starting*2;
                        } else {
                            C = 0;
                        }
                        
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = C+1; i < C+N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        //raise(1, startNodeOp, new GenIntSequentialDistribution(new Integer[]{20}));
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                
                SimulationScenario.StochasticProcess deadLinks1 = new SimulationScenario.StochasticProcess() {
                    {
                        
                        raise(1, deadLinksNMOp, new ConstantDistribution(Integer.class, 1));
                    }
                };
                
                SimulationScenario.StochasticProcess disconnectedNodes1 = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        int N = killing;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        raise(N, disconnectedNodesNMOp, new GenIntSequentialDistribution(lista));
                        //raise(1, disconnectedNodesNMOp, new ConstantDistribution(Integer.class, 1));
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                
                /*
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(300000, startPeers);
                startDeadPeer.startAfterTerminationOf(1000000, startPeers);
                
                //killPeers2.startAfterTerminationOf(20000, startDeadPeer);
                //startDeadPeer2.startAfterTerminationOf(25000, startDeadPeer);
                //startAdditionalPeer.startAfterStartOf(10000, startPeers);
                //stopPeers.startAfterTerminationOf(10000, startPeers);
                //deadLinks1.startAfterTerminationOf(10000,startPeers);
                //disconnectedNodes1.startAfterTerminationOf(10000, startPeers);
                fetchSimulationResult.startAfterTerminationOf(3000000, killPeers);
                terminateAfterTerminationOf(1000, fetchSimulationResult);
                */
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(200000, startPeers);
                //startDeadPeer.startAfterTerminationOf(1000, killPeers);
                //killPeers2.startAfterTerminationOf(20000, startDeadPeer);
                // startDeadPeer2.startAfterTerminationOf(25000, startDeadPeer);
                //startAdditionalPeer.startAfterStartOf(10000, startPeers);
                //stopPeers.startAfterTerminationOf(10000, startPeers);
                //deadLinks1.startAfterTerminationOf(10000,startPeers);
                //disconnectedNodes1.startAfterTerminationOf(10000, startPeers);
                fetchSimulationResult.startAfterTerminationOf(2000000, startPeers);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario simpleKill10(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        //Change here your scenario parameters
        final int starting = 200;
        final int killing = 10;
        final int reviving = 1;  //not used
        final boolean newnodes = false;
        
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(10000, startPeers);
                fetchSimulationResult.startAfterTerminationOf(10000000, killPeers);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario simpleKill50(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        //Change here your scenario parameters
        final int starting = 200;
        final int killing = 50;
        final int reviving = 1;  //not used
        final boolean newnodes = false;
        
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(10000, startPeers);
                fetchSimulationResult.startAfterTerminationOf(10000000, killPeers);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario simpleKill100(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        //Change here your scenario parameters
        final int starting = 200;
        final int killing = 100;
        final int reviving = 1;  //not used
        final boolean newnodes = false;
        
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(10000, startPeers);
                fetchSimulationResult.startAfterTerminationOf(10000000, killPeers);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario simpleKill150(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        //Change here your scenario parameters
        final int starting = 200;
        final int killing = 150;
        final int reviving = 1;  //not used
        final boolean newnodes = false;
        
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 1; i < N*2+1; i++){
                            if(i%2==0)
                                lista[t++] = i;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(10000, startPeers);
                fetchSimulationResult.startAfterTerminationOf(10000000, killPeers);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    public static SimulationScenario natedBoot10open(final long seed) throws Exception {
        SwimScenario.seed = seed;
        
        
        //Change here your scenario parameters
        final int starting = 100;
        final int open = 10;
        final int killing = 10;
        final int reviving = 20;
        final boolean newnodes = false;
        
        
        SimulationScenario scen = new SimulationScenario(){
            {
                SimulationScenario.StochasticProcess startAggregator = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startAggregatorOp, new ConstantDistribution(Integer.class, 0));
                    }
                };
                
                SimulationScenario.StochasticProcess startPeers = new SimulationScenario.StochasticProcess() {
                    {
                        int N = starting;
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 2; i < N*N+2; i++){
                            if(i<2 + open*2){
                                if(i%2==0)
                                    lista[t++] = i;
                            } else {
                                if(i%2==1)
                                    lista[t++] = i;
                            }
                            if (t==N) break;
                        }
                        
                        
                        raise(N, startNodeOp, new GenIntSequentialDistribution(lista));
                    }
                };
                SimulationScenario.StochasticProcess killPeers = new SimulationScenario.StochasticProcess()  {
                    {
                        int N = killing;
                        
                        eventInterArrivalTime(constant(1000));
                        Integer[] lista = new Integer[N];
                        int t = 0;
                        for(int i = 2; i < N+2; i++){
                            lista[t++] = i;
                        }
                        
                        lista[0]=2;
                        lista[1]=6;
                        lista[2]=10;
                        lista[3]=12;
                        lista[4]=16;
                        lista[5]=25;
                        lista[6]=29;
                        lista[7]=69;
                        lista[8]=79;
                        lista[9]=99;
                        
                        
                        //raise(1, killNodeOp, new ConstantDistribution(Integer.class, 20));
                        raise(N, killNodeOp, new GenIntSequentialDistribution(lista));
                        
                    }
                };
                
                SimulationScenario.StochasticProcess fetchSimulationResult = new SimulationScenario.StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, simulationResult);
                    }
                };
                
                startAggregator.start();
                
                
                startPeers.startAfterTerminationOf(1000, startAggregator);
                killPeers.startAfterTerminationOf(200000, startPeers);
                fetchSimulationResult.startAfterTerminationOf(10000000, startPeers);
                terminateAfterTerminationOf(30*1000, fetchSimulationResult);
            }
        };
        scen.setSeed(seed);
        return scen;
    }
    
    
    
}

