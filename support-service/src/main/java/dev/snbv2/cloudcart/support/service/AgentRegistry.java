package dev.snbv2.cloudcart.support.service;

import dev.snbv2.cloudcart.support.agent.Agent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for managing named {@link Agent} instances. Uses a
 * {@link ConcurrentHashMap} to allow safe concurrent registration and lookup
 * of agents by name. Provides methods to register new agents, retrieve agents
 * by name, and list all registered agent names.
 */
public class AgentRegistry {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    /**
     * Registers an agent with the given name. If an agent with the same name
     * already exists, it will be replaced.
     *
     * @param name  the unique name to associate with the agent
     * @param agent the {@link Agent} instance to register
     */
    public void register(String name, Agent agent) {
        agents.put(name, agent);
    }

    /**
     * Retrieves the agent registered under the specified name.
     *
     * @param name the name of the agent to retrieve
     * @return the {@link Agent} registered with the given name
     * @throws IllegalArgumentException if no agent is registered with the given name
     */
    public Agent get(String name) {
        Agent agent = agents.get(name);
        if (agent == null) {
            throw new IllegalArgumentException("Unknown agent: " + name);
        }
        return agent;
    }

    /**
     * Returns the set of all registered agent names.
     *
     * @return an unmodifiable view of the registered agent names
     */
    public Set<String> listAgents() {
        return agents.keySet();
    }
}
