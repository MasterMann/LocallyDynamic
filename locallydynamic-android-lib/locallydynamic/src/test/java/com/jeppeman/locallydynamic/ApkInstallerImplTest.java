package com.jeppeman.locallydynamic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.collect.Lists;
import com.jeppeman.locallydynamic.net.FileUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ApkInstallerImplTest {
    @Mock
    private ApkInstaller.StatusListener mockStatusListener;
    @Mock
    private Logger mockLogger;
    @Spy
    private Context spyContext = ApplicationProvider.getApplicationContext();

    private ApkInstallerImpl apkInstaller;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        apkInstaller = spy(new ApkInstallerImpl(
                spyContext,
                mockStatusListener,
                mockLogger
        ));
    }

    private File createTempFileWithStringContents(String contents) throws IOException {
        File file = new File(spyContext.getFilesDir(), UUID.randomUUID().toString());
        new FileOutputStream(file).write(contents.getBytes("UTF-8"));
        return file;
    }

    @Test
    public void whenApksAreEmpty_install_shouldNotifyFailure() {
        apkInstaller.install(new ArrayList<File>());

        verify(mockStatusListener).onUpdate(any(ApkInstaller.Status.Failed.class));
    }

    @Test
    public void install_shouldHandleSessionProperly() throws IOException {
        List<File> files = Lists.newArrayList(
                createTempFileWithStringContents("hello"),
                createTempFileWithStringContents("hi")
        );
        final List<OutputStream> capturedOutputStreams = new ArrayList<OutputStream>(2);
        PackageInstaller.Session mockSession = mock(PackageInstaller.Session.class);
        final Intent[] installIntent = new Intent[]{null};
        when(mockSession.openWrite(anyString(), anyLong(), anyLong())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                OutputStream mockOutputStream = mock(OutputStream.class);
                capturedOutputStreams.add(mockOutputStream);
                return mockOutputStream;
            }
        });
        doReturn(mockSession).when(apkInstaller).createSession(any(PackageInstaller.SessionParams.class));
        when(apkInstaller.getIntentSenderForSession(any(Intent.class))).thenAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Intent intent = invocation.getArgument(0);
                installIntent[0] = intent;
                return invocation.callRealMethod();
            }
        });

        apkInstaller.install(files);

        InOrder inOrder = inOrder(mockSession, capturedOutputStreams.get(0), capturedOutputStreams.get(1));
        for (int i = 0; i < capturedOutputStreams.size(); i++) {
            OutputStream capturedOutputStream = capturedOutputStreams.get(i);
            File file = files.get(i);
            inOrder.verify(capturedOutputStream).write(FileUtils.readAllBytes(file));
            inOrder.verify(mockSession).fsync(capturedOutputStream);
        }
        inOrder.verify(mockSession).commit(any(IntentSender.class));
        inOrder.verify(mockSession).close();
        inOrder.verifyNoMoreInteractions();
        assertThat(installIntent[0].getComponent()).isEqualTo(
                new ComponentName(spyContext, PackageInstallerResultReceiver.class));
    }

    @Test
    public void install_shouldNotifyInstallingStatus() {
        apkInstaller.install(Lists.newArrayList(new File("")));

        verify(mockStatusListener).onUpdate(any(ApkInstaller.Status.Installing.class));
    }

    @Test
    public void whenIntentSessionIdMismatches_onReceive_shouldNotifyFailure() {
        Intent intent = new Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID, apkInstaller.sessionId + 1);

        apkInstaller.receiveInstallationResult(intent);

        verify(mockStatusListener).onUpdate(any(ApkInstaller.Status.Failed.class));
    }

    @Test
    public void whenStatusIsNotSuccessOrPending_onReceive_shouldNotifyFailure() {
        Intent intent = new Intent()
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, apkInstaller.sessionId)
                .putExtra(PackageInstaller.EXTRA_STATUS, -88);

        apkInstaller.receiveInstallationResult(intent);

        verify(mockStatusListener).onUpdate(any(ApkInstaller.Status.Failed.class));
    }

    @Test
    public void whenStatusIsPending_onReceive_shouldNotifyPending() {
        Intent confirmIntent = new Intent();
        Intent intent = new Intent()
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, apkInstaller.sessionId)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
                .putExtra(Intent.EXTRA_INTENT, confirmIntent);
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);

        apkInstaller.receiveInstallationResult(intent);

        verify(spyContext).startActivity(intentArgumentCaptor.capture());
        verify(mockStatusListener).onUpdate(any(ApkInstaller.Status.Pending.class));
        assertThat(confirmIntent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertThat(intentArgumentCaptor.getValue()).isEqualTo(confirmIntent);
    }

    @Test
    public void whenStatusIsSuccess_onReceive_shouldNotifyInstalled() {
        Intent intent = new Intent()
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, apkInstaller.sessionId)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS);

        apkInstaller.receiveInstallationResult(intent);

        verify(mockStatusListener).onUpdate(any(ApkInstaller.Status.Installed.class));
        verify(spyContext, never()).startActivity(any(Intent.class));
    }
}