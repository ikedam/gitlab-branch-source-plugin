package io.jenkins.plugins.gitlabserverconfig.servers;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.gitlabserverconfig.servers.helpers.GitLabPersonalAccessTokenCreator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the global configuration of GitLab servers.
 */
@Extension
public class GitLabServers extends GlobalConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitLabServers.class);

    /**
     * The list of {@link GitLabServer}, this is subject to the constraint that there can only ever be
     * one entry for each {@link GitLabServer#getServerUrl()}.
     */
    private List<GitLabServer> servers;

    /**
     * A non serializable Map to store a key value pair of server name and its corresponding
     * GitLab Server Configuration for faster filtering
     */
    private Map<String, GitLabServer> serverMap = new HashMap<>();

    /**
     * Constructor.
     */
    public GitLabServers() {
        load();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        servers = req.bindJSONToList(GitLabServer.class, json.get("servers"));
        removeDuplicateServers();
        servers.forEach(server -> LOGGER.info(String.format("Servers: %s", server.getName())));
        refreshServerMap();
        serverMap.keySet().forEach(name -> LOGGER.info(String.format("Server Names: %s", name)));
        save();
        return super.configure(req, json);
    }

    /**
     * Helper function to get predicate to filter servers
     * based on their names
     *
     * @param keyExtractor the Function to filter
     * @param <T> In this case it is server
     * @return a predicate to filter servers list
     */
    private static <T> Predicate<T> distinctByKey(
            Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    /**
     * Remove duplicate server entries
     */
    private void removeDuplicateServers() {
        servers = servers.stream()
                .filter(distinctByKey(GitLabServer::getName))
                .collect(Collectors.toList());
    }

    /**
     * Helper function to pass a method reference to get the GitLab Server using streams to
     * convert a list to map.
     *
     * @param server the server to return
     * @return server
     */
    private static GitLabServer apply(GitLabServer server) {
        return server;
    }

    /**
     * Refreshes serverMap before serialising the object
     */
    private void refreshServerMap() {
        serverMap.clear();
        serverMap = servers.stream()
                .collect(
                        Collectors.toMap(
                                GitLabServer::getName, GitLabServers::apply
                        )
                );
    }

    /**
     * Gets the {@link GitLabServers} singleton.
     *
     * @return the {@link GitLabServers} singleton.
     */
    public static GitLabServers get() {
        return ExtensionList.lookup(GlobalConfiguration.class).get(GitLabServers.class);
    }

    /**
     * Populates a {@link ListBoxModel} with the servers.
     *
     * @return A {@link ListBoxModel} with all the servers
     */
    public ListBoxModel getServerItems() {
        ListBoxModel result = new ListBoxModel();
        for (GitLabServer server : getServers()) {
            String serverUrl = server.getServerUrl();
            String displayName = server.getName();
            result.add(StringUtils.isBlank(displayName) ? serverUrl : displayName + " (" + serverUrl + ")", serverUrl);
        }
        return result;
    }

    /**
     * Gets the list of endpoints.
     *
     * @return the list of endpoints
     */
    @Nonnull
    public List<GitLabServer> getServers() {
        return servers == null || servers.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(servers);
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return Messages.GitLabServers_displayName();
    }

    /**
     * Gets descriptor of {@link GitLabPersonalAccessTokenCreator}
     *
     * returns the list of descriptors
     */
    public List<Descriptor> actions() {
        return Collections.singletonList(Jenkins.get().getDescriptor(GitLabPersonalAccessTokenCreator.class));
    }

    /**
     * Sets the list of GitLab Servers
     *
     * @param servers the list of endpoints.
     */
    public void setServers(@CheckForNull List<? extends GitLabServer> servers) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        this.servers = new ArrayList<>(Util.fixNull(servers));
        serverMap = new HashMap<>();
        for(GitLabServer server: this.servers) {
            serverMap.put(server.getName(), server);
        }
    }

    /**
     * Adds an endpoint
     * Checks if the GitLab Server name is unique
     *
     * @param server the server to add.
     * @return {@code true} if the list of endpoints was modified
     */
    public boolean addServer(@Nonnull GitLabServer server) {
        List<GitLabServer> endpoints = new ArrayList<>(getServers());
        if(!serverMap.containsKey(server.getName())) {
            return false;
        }
        endpoints.add(server);
        setServers(endpoints);
        return true;
    }

    /**
     * Updates an existing endpoint (or adds if missing)
     * Checks if the GitLab Server name is matched
     *
     * @param server the server to update.
     * @return {@code true} if the list of endpoints was modified
     */
    public boolean updateServer(@Nonnull GitLabServer server) {
        if (!serverMap.containsKey(server.getName())) {
            return false;
        }
        List<GitLabServer> servers =  getServers().stream()
                .map(oldServer -> oldServer.getName().equals(server.getName()) ? server : oldServer)
                .collect(Collectors.toList());
        setServers(servers);
        return true;
    }

    /**
     * Removes a server entry
     * Checks if the GitLab Server name is matched
     *
     * @param name the server name to remove.
     * @return {@code true} if the list of endpoints was modified
     */
    public boolean removeServer(@CheckForNull String name) {
        if(!serverMap.containsKey(name)) {
            return false;
        }
        List<GitLabServer> servers = new ArrayList<>(getServers());
        servers.removeIf(server -> server.getName().equals(name));
        setServers(servers);
        return true;
    }
}
