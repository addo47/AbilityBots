package org.telegram.abilitybots.api.objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.telegram.telegrambots.api.objects.User;

import java.io.Serializable;

import static com.google.common.base.Strings.isNullOrEmpty;

public class EndUser implements Serializable {
    @JsonProperty("name")
    private final String name;
    @JsonProperty("id")
    private final Integer id;
    @JsonProperty("username")
    private final String username;

    @JsonCreator
    public EndUser(@JsonProperty("name") String name, @JsonProperty("id") Integer id, @JsonProperty("username")String username) {
        this.name = name;
        this.id = id;
        this.username = username;
    }

    public EndUser(User user) {
        name = user.getFirstName() + (isNullOrEmpty(user.getLastName()) ? "" : " " + user.getLastName());
        id = user.getId();
        username = user.getUserName();
    }

    public String username() {
        return username;
    }

    public String name() {
        return name;
    }

    public int id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        EndUser mUser = (EndUser) o;
        return Objects.equal(id, mUser.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("id", id)
                .add("creatorId", username)
                .toString();
    }
}
