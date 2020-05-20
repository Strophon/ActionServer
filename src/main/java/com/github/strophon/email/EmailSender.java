package com.github.strophon.email;

import javax.mail.internet.InternetAddress;

public interface EmailSender {

    public boolean sendRegistrationEmail(int userId, InternetAddress email,
                                         String name, String emailToken);

    public boolean sendRecoveryEmail(int userId, InternetAddress email,
                                     String name, String recoveryToken);
}
