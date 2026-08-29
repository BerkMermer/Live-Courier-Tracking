package com.berk.courier_tracking_api.exception;

import org.springframework.http.HttpStatus;

/** Business-rule error codes mapped to HTTP status and default messages. */
public enum ErrorCode {

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Bu email adresi zaten kayıtlı"),
    PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Bu telefon numarası zaten kayıtlı"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email veya şifre hatalı"),
    ORDER_ALREADY_TERMINAL(HttpStatus.CONFLICT, "Tamamlanmış veya iptal edilmiş sipariş üzerinde işlem yapılamaz"),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Bu durumdaki sipariş iptal edilemez"),
    ORDER_NOT_PICKABLE(HttpStatus.CONFLICT, "Sipariş alış için ASSIGNED olmalıdır"),
    ORDER_NOT_DELIVERABLE(HttpStatus.CONFLICT, "Sipariş teslim için PICKED_UP olmalıdır"),
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
