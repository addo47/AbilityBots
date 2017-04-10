package org.telegram.abilitybots.api.objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.telegram.telegrambots.api.objects.User;

import java.io.Serializable;
import java.util.Objects;
import java.util.StringJoiner;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class EndUser implements Serializable {
    @JsonProperty("id")
    private final Integer id;
    @JsonProperty("firstName")
    private final String firstName;
    @JsonProperty("lastName")
    private final String lastName;
    @JsonProperty("username")
    private final String username;

    @JsonCreator
    public EndUser(@JsonProperty("id") Integer id,
                   @JsonProperty("firstName") String firstName,
                   @JsonProperty("lastName") String lastName,
                   @JsonProperty("username") String username) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
    }

    public static EndUser fromUser(User user) {
        return new EndUser(user.getId(), user.getFirstName(), user.getLastName(), user.getUserName());
    }

    public int id() {
        return id;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public String username() {
        return username;
    }

    public String fullName() {
        StringJoiner name = new StringJoiner(" ");

        if (!isEmpty(firstName))
            name.add(firstName);
        if (!isEmpty(lastName))
            name.add(lastName);

        return name.toString();
    }

    public String shortName() {
        if (!isEmpty(firstName))
            return firstName;

        if (!isEmpty(lastName))
            return lastName;

        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        EndUser endUser = (EndUser) o;
        return Objects.equals(id, endUser.id) &&
                Objects.equals(firstName, endUser.firstName) &&
                Objects.equals(lastName, endUser.lastName) &&
                Objects.equals(username, endUser.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, firstName, lastName, username);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("firstName", firstName)
                .add("lastName", lastName)
                .add("username", username)
                .toString();
    }
}
