/*******************************************************************************
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 *******************************************************************************/
package fr.gouv.vitam.common.auth.web.filter;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.*;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.subject.Subject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import fr.gouv.vitam.common.shiro.junit.AbstractShiroTest;
import sun.misc.BASE64Encoder;
import sun.security.provider.X509Factory;

public class X509AuthenticationFilterTest extends AbstractShiroTest {
    private X509Certificate cert;
    private String pem;

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    HttpServletRequest requestNull = mock(HttpServletRequest.class);

    private X509Certificate loadCertificate(CertificateFactory cf,File f) throws CertificateException, IOException {
        FileInputStream in=new FileInputStream(f);
        try {
            cert =(X509Certificate)cf.generateCertificate(in);
            cert.checkValidity();
            return cert;
        }
        finally {
            in.close();
        }
    }


    @Before
    public void setUp() throws Exception {

        generateX509Certificate();

        x509CertificateToPem();



        when(request.getRemoteHost()).thenReturn("127.0.0.1");

        when(requestNull.getRemoteHost()).thenReturn("127.0.0.1");
    }

    private void x509CertificateToPem() throws CertificateEncodingException {
        BASE64Encoder encoder = new BASE64Encoder();

        StringWriter sw = new StringWriter();
        sw.write(X509Factory.BEGIN_CERT);
        sw.write("\n");
        sw.write(encoder.encode(cert.getEncoded()));
        sw.write("\n");
        sw.write(X509Factory.END_CERT);

        pem = sw.toString();
    }

    private void generateX509Certificate()
        throws NoSuchAlgorithmException, IOException, OperatorCreationException, CertificateException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024, new SecureRandom());
        KeyPair keyPair=  generator.generateKeyPair();

        String dn= "localhost";

        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
            new X500Name("CN=" + dn),
            BigInteger.valueOf(new SecureRandom().nextLong()),
            new Date(System.currentTimeMillis() - 10000),
            new Date(System.currentTimeMillis() + 24L*3600*1000),
            new X500Name("CN=" + dn),
            SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));

        builder.addExtension(X509Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(X509Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(X509Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));

        AlgorithmIdentifier signatureAlgorithmId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA");
        AlgorithmIdentifier digestAlgorithmId = new DefaultDigestAlgorithmIdentifierFinder().find(signatureAlgorithmId);
        AsymmetricKeyParameter privateKey = PrivateKeyFactory.createKey(keyPair.getPrivate().getEncoded());


        X509CertificateHolder
            holder =  builder.build(new BcRSAContentSignerBuilder(signatureAlgorithmId, digestAlgorithmId).build(privateKey));
        Certificate certificate = holder.toASN1Structure();

        InputStream is = new ByteArrayInputStream(certificate.getEncoded());
        try {
            cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(is);
        } finally {
            is.close();
        }
    }

    @After
    public void tearDownSubject() {
        clearSubject();
    }

    @Test
    public void givenFilterAccessDenied() throws Exception {
        // Needs mock subject for login call
        when(request.getAttribute("javax.servlet.request.X509Certificate")).thenReturn(new X509Certificate[] {cert});
        Subject subjectUnderTest = mock(Subject.class);
        Mockito.doNothing().when(subjectUnderTest).login(anyObject());
        setSubject(subjectUnderTest);

        final X509AuthenticationFilter filter = new X509AuthenticationFilter();
        filter.onAccessDenied(request, response);
    }

    @Test
    public void givenFilterCreateToken() throws Exception {
        when(request.getAttribute("javax.servlet.request.X509Certificate")).thenReturn(new X509Certificate[] {cert});
        final X509AuthenticationFilter filter = new X509AuthenticationFilter();
        filter.createToken(request, response);
    }

    @Test(expected = Exception.class)
    public void givenFilterCreateTokenWhenClientCertChainNullThenThrowException() throws Exception {
        final X509AuthenticationFilter filter = new X509AuthenticationFilter();
        filter.createToken(requestNull, response);
    }

    @Test
    public void givenFilterByHeaderAccessDenied() throws Exception {
        // Needs mock subject for login call
        when(request.getHeader(X509AuthenticationFilter.X_SSL_CLIENT_CERT)).thenReturn(pem);
        Subject subjectUnderTest = mock(Subject.class);
        Mockito.doNothing().when(subjectUnderTest).login(anyObject());
        setSubject(subjectUnderTest);

        final X509AuthenticationFilter filter = new X509AuthenticationFilter();
        filter.setUseHeader(true);
        filter.onAccessDenied(request, response);
    }

    @Test
    public void givenFilterByHeaderCreateToken() throws Exception {
        when(request.getHeader(X509AuthenticationFilter.X_SSL_CLIENT_CERT)).thenReturn(pem);
        final X509AuthenticationFilter filter = new X509AuthenticationFilter();
        filter.setUseHeader(true);
        filter.createToken(request, response);
    }

    @Test(expected = Exception.class)
    public void givenFilterByHeaderCreateTokenWhenClientCertChainNullThenThrowException() throws Exception {
        final X509AuthenticationFilter filter = new X509AuthenticationFilter();
        filter.setUseHeader(true);
        filter.createToken(requestNull, response);
    }

}
