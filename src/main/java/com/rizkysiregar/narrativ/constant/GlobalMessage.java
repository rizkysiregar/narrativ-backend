package com.rizkysiregar.narrativ.constant;

import lombok.Getter;

@Getter
public enum GlobalMessage {

    SUCCESS("200", "Your request was processed successfully."),
    CREATED("201", "New data was added successfully!"),
    INTERNAL_ERROR("500", "Something went wrong on our server. Please wait a moment and try again."),
    BAD_REQUEST("400", "Sorry, we could not process your data. Please check your input."),
    TOO_MANY_REQUEST("429", "Too many requests. Please take a short break before trying again."),
    RATE_LIMIT("429", "Hang on, we are very busy right now. Please try again in {0} seconds!"),
    TOO_MANY_REQUEST_OTP("429", "You have reached the attempt limit. Please request a new OTP code in 5 minutes."),
    BAD_REQUEST_FILE_TO_LARGE("400", "The file is too large. Please upload a smaller file."),
    UPLOAD_NOT_VERIFIED("503", "We could not check your upload right now. Please try again in a moment."),
    NOT_FOUND("404", "Oops, the data you are looking for was not found."),
    UNAUTHORIZED("401", "Your session has ended. Please sign in again."),
    ACCESS_DENIED("403", "Access denied: you do not have permission to access this resource."),
    GOOGLE_UNAUTHORIZED("401", "Invalid Google token."),
    GOOGLE_TOKEN_MISS_MATCH("401", "Google token client mismatch."),
    GOOGLE_TOKEN_FAILED_TO_VERIFY("401", "Failed to verify the Google token."),
    GOOGLE_EMAIL_NOT_VERIFIED("401", "Your Google account email has not been verified."),
    BAD_CREDENTIALS("401", "The email or password you entered is incorrect. Please try again."),
    USER_DISABLED("401", "Your account has been deactivated. Please contact an admin to reactivate it."),
    PARAMETER_NOT_FOUND("404", "Supporting data was not found. Please start the process again."),
    INVALID_OTP("400", "The OTP code you entered is incorrect. Please check the code again."),
    OTP_RESEND_COOLDOWN("429", "A new OTP code was just sent. Please wait 1 minute before requesting another."),
    OTP_EXPIRED("404", "The OTP code is no longer valid. Please request a new one."),
    INVALID_RESET_TOKEN("400", "The password reset link is no longer valid or has already been used."),
    USERNAME_ALREADY_TAKEN("400", "This username is already taken. Try another unique one!"),
    USER_NOT_FOUND("404", "The user you are looking for is not registered."),
    USER_BANNED("401", "Your account has been banned by an admin."),
    USER_BANNED_REASON("401", "Your account has been banned by an admin. Reason: {0}"),
    USER_ALREADY_BANNED("409", "This user is already banned."),
    USER_NOT_BANNED("409", "This user is not banned."),
    USER_ALREADY_DELETED("409", "This user has deleted their own account, so it cannot be moderated."),
    CANNOT_BAN_SELF("403", "You cannot ban your own account."),
    CANNOT_BAN_ADMIN("403", "An admin cannot be banned. Demote the admin role first."),
    CANNOT_CHANGE_OWN_ROLE("403", "You cannot change the role of your own account."),
    LAST_ADMIN("409", "The last admin cannot be demoted."),
    CONTENT_ALREADY_TAKEN_DOWN("409", "This content has already been taken down."),
    CONTENT_NOT_TAKEN_DOWN("409", "This content is not taken down."),
    POST_ALREADY_DELETED("409", "This post was deleted by its owner, so it cannot be moderated."),
    REPORT_ALREADY_EXISTS("409", "You have already reported this."),
    CANNOT_REPORT_OWN("400", "You cannot report yourself or your own content."),
    REPORT_DESCRIPTION_REQUIRED("400", "A description is required when the report reason is OTHER."),
    REPORT_ALREADY_CLOSED("409", "This report is already closed."),
    REPORT_ACTION_NOT_APPLICABLE("400", "This action does not apply to this type of report target."),
    REPORT_NOTE_REQUIRED("400", "A note of 5 to 500 characters is required for this action."),
    PARAMETER_KEY_EXISTS("409", "A parameter with this key already exists."),
    TEMPLATE_CODE_EXISTS("409", "A template with this code already exists."),
    NOTHING_TO_UPDATE("400", "Nothing to update. Fill in at least one field."),
    BROADCAST_FAILED("502", "The notification could not be sent to Firebase. Please try again later."),
    CANNOT_DELETE_ADMIN("403", "An admin account cannot be deleted from the app."),
    CONFIRMATION_FAILED("403", "Confirmation failed. Please check your password or Google account."),
    CANNOT_BLOCK_SELF("400", "You cannot block yourself."),
    EMAIL_ALREADY_TAKEN("400", "This email is already registered. Please use another email or sign in to your account."),
    EMAIL_CHANGE_NOT_ALLOWED("400", "The email cannot be changed here, it has to be verified first. Please use Change email in the settings."),
    EMAIL_UNCHANGED("400", "This is already the email of your account."),
    APP_CHECK_FAILED("403", "The app could not be verified. Update NARRATIV from the Play Store and try again."),
    INVALID_PARAMETER("400", "Invalid value for parameter ''{0}''"),
    EMAIL_NOT_VERIFIED("400", "Your email has not been verified. Please request an OTP code and verify your email first."),
    ;


    private final String code;
    /** English text: the default when no translation exists for the caller's language. MessageFormat syntax. */
    private final String message;

    /** Stable, language independent identifier clients can switch on (the HTTP status in {@link #code} is not unique). */
    public String getErrorCode() {
        return name();
    }

    GlobalMessage(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
