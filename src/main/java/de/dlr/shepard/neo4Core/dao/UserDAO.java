package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dlr.shepard.neo4Core.entities.User;

public class UserDAO extends GenericDAO<User> {

	public User find(String username) {
		User entity = session.load(getEntityType(), username, DEPTH_ENTITY);
		return entity;
	}

	public boolean delete(String username) {
		User entity = session.load(getEntityType(), username);
		if (entity != null) {
			session.delete(entity);
			return true;
		}
		return false;
	}

	public List<User> searchUsers(String username, String firstName, String lastName, String email) {
		String query = "";
		query = query + "MATCH (u:User) WHERE ";
		Map<String, Object> paramsMap = new HashMap<String, Object>();
		if (username != null)
			paramsMap.put("username", username);
		if (firstName != null)
			paramsMap.put("firstName", firstName);
		if (lastName != null)
			paramsMap.put("lastName", lastName);
		if (email != null)
			paramsMap.put("email", email);
		ArrayList<String> whereClauses = new ArrayList<String>(paramsMap.size());
		for (String key : paramsMap.keySet())
			whereClauses.add("u." + key + " =~ $" + key + "");
		query = query + String.join(" AND ", whereClauses) + " ";
		query = query + "RETURN u";
		var result = findByQuery(query, paramsMap);
		ArrayList<User> ret = new ArrayList<User>();
		for (User user : result)
			ret.add(user);
		return ret;
	}

	@Override
	public Class<User> getEntityType() {
		return User.class;
	}
}
