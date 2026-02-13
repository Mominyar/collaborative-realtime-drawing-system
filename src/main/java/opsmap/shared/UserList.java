package opsmap.shared;

import java.io.Serializable;
import java.util.List;

public class UserList implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String> usernames;

    public UserList(List<String> usernames) {
        this.usernames = usernames;
    }

    public List<String> getUsernames() {
        return usernames;
    }
}
