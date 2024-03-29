package reserve.global.exception;

import lombok.Getter;

@Getter
public class ErrorCodeException extends RuntimeException {

    private final ErrorCode errorCode;

    public ErrorCodeException(ErrorCode errorCode) {
        super(errorCode.toString());
        this.errorCode = errorCode;
    }

    public ErrorCodeException(ErrorCode errorCode, Throwable e) {
        super(errorCode.toString(), e);
        this.errorCode = errorCode;
    }

}
