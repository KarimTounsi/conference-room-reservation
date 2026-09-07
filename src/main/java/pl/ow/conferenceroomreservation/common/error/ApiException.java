package pl.ow.conferenceroomreservation.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base for every failure this API answers with a described problem rather than a bare status.
 *
 * <p>The exception carries its own status, RFC 9457 type URI and title, so one handler method can
 * translate all of them. The type URI is the stable part of the contract - clients may branch on
 * it, unlike on the detail text.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String title;

    protected ApiException(HttpStatus status, String type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }
}
