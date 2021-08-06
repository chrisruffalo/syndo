package io.github.chrisruffalo.syndo.executions.actions.impl;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.server.mock.EnableOpenShiftMockClient;
import io.github.chrisruffalo.syndo.TestUtil;
import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.cmd.CommandBuild;
import io.github.chrisruffalo.syndo.config.Loader;
import io.github.chrisruffalo.syndo.exceptions.SyndoException;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import org.junit.Before;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * Tests that build files resolve as expected
 *
 */
@EnableOpenShiftMockClient(crud = true)
public class BuildResolveActionTest {

    private static final String NAMESPACE = "syndo-test";

    /**
     * Client is injected by @EnableOpenShiftMockClient
     */
    OpenShiftClient client;

    @Before
    public void init() {
        client.namespaces().createOrReplace(
            new NamespaceBuilder()
            .withNewMetadata()
            .withName(NAMESPACE)
            .endMetadata()
            .build()
        );
    }

    /**
     * Simple test of a basic component list.
     *
     * @throws SyndoException when a syndo component encounters an error
     */
    @Test
    public void testBasicResolve() throws SyndoException {
        final BuildContext context = this.processBuild("test-builds/basic.yml");

        // expect that the first item in the build order is "alpha"
        Assertions.assertEquals("alpha", context.getBuildOrder().get(0).getComponent().getName());
        // and the second one is beta
        Assertions.assertEquals("beta", context.getBuildOrder().get(1).getComponent().getName());
    }

    /**
     * This test is just a component list that is "backwards" that is to say that the first
     * element is dependent on the second so that the build order should be the reverse of
     * the way that the list is presented
     *
     * @throws SyndoException when a syndo component encounters an error
     */
    @Test
    public void testReverseOrder() throws SyndoException {
        final BuildContext context = this.processBuild("test-builds/reverse.yml");

        // expect in reverse order
        Assertions.assertEquals("november", context.getBuildOrder().get(0).getComponent().getName());
        Assertions.assertEquals("xray", context.getBuildOrder().get(1).getComponent().getName());
        Assertions.assertEquals("zed", context.getBuildOrder().get(2).getComponent().getName());
    }

    /**
     * Ensure that transient build items are picked up and added to the build
     *
     * @throws SyndoException when a syndo component encounters an error
     */
    @Test
    public void testTransient() throws SyndoException {
        // set up custom build command for single component
        final Command command = this.getDefaultBuildCommand();
        command.getBuild().getComponents().clear();
        command.getBuild().getComponents().add("charlie");

        final BuildContext context = this.processBuild("test-builds/transient.yml");

        // expect in reverse order
        Assertions.assertEquals("alpha", context.getBuildOrder().get(0).getComponent().getName());
        Assertions.assertEquals("beta", context.getBuildOrder().get(1).getComponent().getName());
        Assertions.assertEquals("charlie", context.getBuildOrder().get(2).getComponent().getName());
    }

    private Command getDefaultBuildCommand() {
        final Command command = new Command();
        final CommandBuild build = new CommandBuild();
        build.setNamespace(NAMESPACE);
        command.setBuild(build);
        command.setParsedCommand("build");
        return command;
    }

    private BuildContext processBuild(final Command command, final String buildFileResourcePath) throws SyndoException {
        final Path buildFile = TestUtil.getTestResourcePath(buildFileResourcePath);
        final BuildContext context = new BuildContext();
        context.setNamespace(NAMESPACE);
        context.setClient(client);
        context.setConfig(Loader.read(buildFile));
        context.setConfigPath(buildFile);
        context.setCommand(command);
        if (context.getCommandBuild() == null) {
            context.setCommandBuild(command.getBuild());
        }
        final BuildPrepareAction prepare = new BuildPrepareAction();
        prepare.execute(context);
        final BuildResolveAction action = new BuildResolveAction();
        action.execute(context);
        return context;
    }

    private BuildContext processBuild(final String buildFileResourcePath) throws SyndoException {
        return this.processBuild(this.getDefaultBuildCommand(), buildFileResourcePath);
    }

}
