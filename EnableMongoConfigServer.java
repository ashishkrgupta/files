import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Configuration
@Import(MongoEnvRepoConfig.class)
@EnableConfigServer
public @interface EnableMongoConfigServer {

}




import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoEnvRepoConfig {
	@Autowired
	private MongoTemplate mongoDbTemp;

	@Bean
	public EnvironmentRepository environmentRepository() {
		return new MongoEnvRepo(mongoDbTemp);
	}

}






import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.config.YamlProcessor;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.StringUtils;

@Configuration
public class MongoEnvRepo implements EnvironmentRepository {

	private static final String PROFILE = "profile";
	private static final String DEFAULT = "default";
	private static final String DEFAULT_PROFILE = null;

	private MongoTemplate template;
	private MapFlattener mapFlattener;
	

	public MongoEnvRepo(MongoTemplate template) {
		this.template = template;
		this.mapFlattener = new MapFlattener();
	}

	@Override
	public Environment findOne(String name, String profile, String label) {
		String[] profilesArr = StringUtils.commaDelimitedListToStringArray(profile);
		List<String> profiles = new ArrayList<String>(Arrays.asList(profilesArr.clone()));
		for (int i = 0; i < profiles.size(); i++) {
			if (DEFAULT.equals(profiles.get(i))) {
				profiles.set(i, DEFAULT_PROFILE);
			}
		}
		profiles.add(DEFAULT_PROFILE); // Default configuration will have 'null' profile
		profiles = sortedUnique(profiles);

		Query query = new Query();
		query.addCriteria(Criteria.where(PROFILE).in(profiles.toArray()));

		Environment environment;
		try {
			List<MongoPropertySource> sources = mongoTemplate.find(query, MongoPropertySource.class, name);
			sortSourcesByProfile(sources, profiles);
			environment = new Environment(name, profilesArr, label, null, null);
			for (MongoPropertySource propertySource : sources) {
				String prof = propertySource.getProfile() != null ? propertySource.getProfile() : DEFAULT;
				String sourceName = String.format("%s-%s", name, prof);
				Map<String, Object> flatSource = mapFlattener.flatten(propertySource.getSource());
				PropertySource propSource = new PropertySource(sourceName, flatSource);
				environment.add(propSource);
			}
		} catch (Exception e) {
			throw new IllegalStateException("Cannot load environment", e);
		}

		return environment;
	}

	private ArrayList<String> sortedUnique(List<String> values) {
		return new ArrayList<String>(new LinkedHashSet<String>(values));
	}

	private void sortSourcesByProfile(List<MongoPropertySource> sources, final List<String> profiles) {
		Collections.sort(sources, new Comparator<MongoPropertySource>() {
			@Override
			public int compare(MongoPropertySource s1, MongoPropertySource s2) {
				int i1 = profiles.indexOf(s1.getProfile());
				int i2 = profiles.indexOf(s2.getProfile());
				return Integer.compare(i1, i2);
			}
		});
	}

	public static class MongoPropertySource {
		private String profile;
		private LinkedHashMap<String, Object> source = new LinkedHashMap<String, Object>();

		public String getProfile() {
			return profile;
		}

		public void setProfile(String profile) {
			this.profile = profile;
		}
		
		public LinkedHashMap<String, Object> getSource() {
			return source;
		}

		public void setSource(LinkedHashMap<String, Object> source) {
			this.source = source;
		}
	}

	private static class MapFlattener extends YamlProcessor {
		public Map<String, Object> flatten(Map<String, Object> source) {
			return getFlattenedMap(source);
		}
	}

}



bootstrap.pprop

spring.data.mongodb.uri=mongodb://localhost/config-db
