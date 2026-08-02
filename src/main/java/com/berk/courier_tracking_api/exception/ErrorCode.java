package com.berk.courier_tracking_api.exception;

import org.springframework.http.HttpStatus;

/** Business-rule error codes mapped to HTTP status and default messages. */
public enum ErrorCode {

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Bu email adresi zaten kayıtlı"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email veya şifre hatalı"),
    ORDER_ALREADY_TERMINAL(HttpStatus.CONFLICT, "Tamamlanmış veya iptal edilmiş sipariş üzerinde işlem yapılamaz"),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Bu durumdaki sipariş iptal edilemez"),
    COURIER_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "Bu siparişe zaten kurye atanmış"),
    NO_AVAILABLE_COURIER(HttpStatus.CONFLICT, "Şu anda müsait kurye bulunmamaktadır");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
