package io.jenkins.plugins.credentials.secretsmanager;

import com.amazonaws.services.secretsmanager.model.CreateSecretRequest;
import com.amazonaws.services.secretsmanager.model.CreateSecretResult;
import com.amazonaws.services.secretsmanager.model.Tag;
import com.cloudbees.plugins.credentials.CredentialsUnavailableException;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import hudson.model.Result;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.credentials.secretsmanager.factory.Type;
import io.jenkins.plugins.credentials.secretsmanager.util.*;
import io.jenkins.plugins.credentials.secretsmanager.util.assertions.CustomSoftAssertions;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.List;

import static io.jenkins.plugins.credentials.secretsmanager.util.assertions.CustomAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * The plugin should support Certificate credentials.
 */
public class StandardCertificateCredentialsIT implements CredentialsTests {

    private static final String ALIAS = "test";
    private static final KeyPair KEY_PAIR = Crypto.newKeyPair();
    private static final char[] PASSWORD = new char[]{};
    private static final String CN = "CN=localhost";
    private static final Certificate[] CERTIFICATE_CHAIN = { Crypto.newSelfSignedCertificate(CN, KEY_PAIR) };

    public final MyJenkinsConfiguredWithCodeRule jenkins = new MyJenkinsConfiguredWithCodeRule();
    public final AWSSecretsManagerRule secretsManager = new AWSSecretsManagerRule();

    @Rule
    public final TestRule chain = Rules.jenkinsWithSecretsManager(jenkins, secretsManager);

    @Test
    @ConfiguredWithCode(value = "/integration.yml")
    public void shouldSupportListView() {
        // Given
        final KeyStore keyStore = Crypto.singletonKeyStore(ALIAS, KEY_PAIR.getPrivate(), PASSWORD, CERTIFICATE_CHAIN);
        final CreateSecretResult foo = createCertificateSecret(Crypto.save(keyStore, PASSWORD));

        // When
        final ListBoxModel list = jenkins.getCredentials().list(StandardCertificateCredentials.class);

        // Then
        assertThat(list)
                .containsOption(CN, foo.getName());
    }

    @Test
    @ConfiguredWithCode(value = "/integration.yml")
    public void shouldHaveDescriptorIcon() {
        final byte[] keystore = Crypto.save(Crypto.singletonKeyStore(ALIAS, KEY_PAIR.getPrivate(), PASSWORD, CERTIFICATE_CHAIN), PASSWORD);
        final CreateSecretResult foo = createCertificateSecret(keystore);
        final StandardCertificateCredentials ours = lookup(StandardCertificateCredentials.class, foo.getName());

        final StandardCertificateCredentials theirs = new CertificateCredentialsImpl(null, "id", "description", "password", new CertificateCredentialsImpl.UploadedKeyStoreSource(SecretBytes.fromBytes(keystore)));

        assertThat(ours)
                .hasSameDescriptorIconAs(theirs);
    }

    @Test
    @ConfiguredWithCode(value = "/integration.yml")
    public void shouldHaveId() {
        // Given
        final KeyStore keyStore = Crypto.singletonKeyStore(ALIAS, KEY_PAIR.getPrivate(), PASSWORD, CERTIFICATE_CHAIN);
        final CreateSecretResult foo = createCertificateSecret(Crypto.save(keyStore, PASSWORD));

        // When
        final StandardCertificateCredentials credential = lookup(StandardCertificateCredentials.class, foo.getName());

        // Then
        assertThat(credential)
                .hasId(foo.getName());
    }

    @Test
    @ConfiguredWithCode(value = "/integration.yml")
    public void shouldHaveEmptyPassword() {
        // Given
        final KeyStore keyStore = Crypto.singletonKeyStore(ALIAS, KEY_PAIR.getPrivate(), PASSWORD, CERTIFICATE_CHAIN);
        final CreateSecretResult foo = createCertificateSecret(Crypto.save(keyStore, PASSWORD));

        // When
        final StandardCertificateCredentials credential = lookup(StandardCertificateCredentials.class, foo.getName());

        // Then
        assertThat(credential)
                .doesNotHavePassword();
    }

    @Test
    @ConfiguredWithCode(value = "/integration.yml")
    public void shouldHaveKeystore() {
        // Given
        final KeyStore keyStore = Crypto.singletonKeyStore(ALIAS, KEY_PAIR.getPrivate(), PASSWORD, CERTIFICATE_CHAIN);
        final CreateSecretResult foo = createCertificateSecret(Crypto.save(keyStore, PASSWORD));

        // When
        final StandardCertificateCredentials credential = lookup(StandardCertificateCredentials.class, foo.getName());

        // Then
        assertThat(credential.getKeyStore())
                .containsEntry(ALIAS, CERTIFICATE_CHAIN);
    }

    @Test
    @ConfiguredWithCode(value = "/integration.yml")
    public void shouldSupportWithCredentialsBinding() {
        // Given
        final KeyStore keyStore = Crypto.singletonKeyStore(ALIAS, KEY_PAIR.getPrivate(), PASSWORD, CERTIFICATE_CHAIN);
        final CreateSecretResult foo = createCertificateSecret(Crypto.save(keyStore, PASSWORD));

        // When
        final WorkflowRun run = runPipeline("",
                "node {",
                "  withCredentials([certificate(credentialsId: '" + foo.getName() + "', keystoreVariable: 'KEYSTORE')]) {",
                "    echo \"Credential: {keystore: $KEYSTORE}\"",
                "  }",
                "}");

        // Then
        assertThat(run)
                .hasResult(Result.SUCCESS)
                .hasLogContaining("Credential: {keystore: ****}");
    }

    @Ignore("Declarative Pipeline does not support certificate bindings")
    public void shouldSupportEnvironmentBinding() {

    }

    @Test
    @ConfiguredWithCode(value = "/integration.yml")
    public void shouldSupportSnapshots() {
        // Given
        final KeyStore keyStore = Crypto.singletonKeyStore(ALIAS, KEY_PAIR.getPrivate(), PASSWORD, CERTIFICATE_CHAIN);
        final CreateSecretResult foo = createCertificateSecret(Crypto.save(keyStore, PASSWORD));
        final StandardCertificateCredentials before = lookup(StandardCertificateCredentials.class, foo.getName());

        // When
        final StandardCertificateCredentials after = CredentialSnapshots.snapshot(before);

        // Then
        final CustomSoftAssertions s = new CustomSoftAssertions();
        s.assertThat(after).hasId(before.getId());
        s.assertThat(after).hasPassword(before.getPassword());
        s.assertThat(after.getKeyStore()).containsEntry(ALIAS, CERTIFICATE_CHAIN);
        s.assertAll();
    }

    @Test
    @ConfiguredWithCode(value = "/integration.yml")
    public void shouldNotTolerateMalformattedKeyStore() {
        // Given
        final CreateSecretResult foo = createCertificateSecret(new byte[] {0x00, 0x01});

        // When
        final StandardCertificateCredentials credential = jenkins.getCredentials().lookup(StandardCertificateCredentials.class, foo.getName());

        // Then
        assertThatThrownBy(credential::getKeyStore)
                .isInstanceOf(CredentialsUnavailableException.class);
    }

    private CreateSecretResult createCertificateSecret(byte[] secretBinary) {
        final List<Tag> tags = Lists.of(AwsTags.type(Type.certificate));

        final CreateSecretRequest request = new CreateSecretRequest()
                .withName(CredentialNames.random())
                .withSecretBinary(ByteBuffer.wrap(secretBinary))
                .withTags(tags);

        return secretsManager.getClient().createSecret(request);
    }

    private <C extends StandardCredentials> C lookup(Class<C> type, String id) {
        return jenkins.getCredentials().lookup(type, id);
    }

    private WorkflowRun runPipeline(String... pipeline) {
        return jenkins.getPipelines().run(Strings.m(pipeline));
    }
}
