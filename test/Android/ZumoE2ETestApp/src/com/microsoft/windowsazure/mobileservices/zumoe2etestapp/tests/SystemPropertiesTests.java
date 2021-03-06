/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Apache 2.0 License
 
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
 
     http://www.apache.org/licenses/LICENSE-2.0
 
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 
See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */
package com.microsoft.windowsazure.mobileservices.zumoe2etestapp.tests;

import static com.microsoft.windowsazure.mobileservices.MobileServiceQueryOperations.field;
import static com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.Util.filter;
import static com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.Util.compare;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Pair;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceJsonTable;
import com.microsoft.windowsazure.mobileservices.MobileServicePreconditionFailedException;
import com.microsoft.windowsazure.mobileservices.MobileServicePreconditionFailedExceptionBase;
import com.microsoft.windowsazure.mobileservices.MobileServiceQuery;
import com.microsoft.windowsazure.mobileservices.MobileServiceSystemProperty;
import com.microsoft.windowsazure.mobileservices.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.TableDeleteCallback;
import com.microsoft.windowsazure.mobileservices.TableJsonOperationCallback;
import com.microsoft.windowsazure.mobileservices.TableOperationCallback;
import com.microsoft.windowsazure.mobileservices.TableQueryCallback;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.ExpectedValueException;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.TestCase;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.TestExecutionCallback;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.TestGroup;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.TestResult;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.TestStatus;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.framework.Util.IPredicate;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.tests.types.StringIdJsonElement;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.tests.types.StringIdRoundTripTableElement;
import com.microsoft.windowsazure.mobileservices.zumoe2etestapp.tests.types.SystemPropertiesTestData;

public class SystemPropertiesTests extends TestGroup {

	class ResultsContainer<T> {
		private Exception mException;
		private T mItem;
		private List<T> mItems;

		public Exception getException() {
			return mException;
		}

		public void setException(Exception exception) {
			this.mException = exception;
		}

		public T getItem() {
			return mItem;
		}

		public void setItem(T item) {
			this.mItem = item;
		}

		public List<T> getItems() {
			return mItems;
		}

		public void setItems(List<T> items) {
			this.mItems = items;
		}

	}

	protected static final String STRING_ID_TABLE_NAME = "stringIdRoundTripTable";

	public SystemPropertiesTests() {
		super("System Properties tests");

		this.addTest(createTypeSystemPropertiesTest("Operations with All System Properties from Type"));
		this.addTest(createCustomSystemPropertiesTest("Operations with Custom System Properties set on Table"));
		for (String systemProperties : SystemPropertiesTestData.ValidSystemPropertyQueryStrings) {
			this.addTest(createQueryParameterSystemPropertiesTest("Operations with Query Parameter System Properties set on Table - " + systemProperties,
					systemProperties));
		}
		this.addTest(createMergeConflictTest("Merge Conflict"));
		this.addTest(createMergeConflictGenericTest("Merge Conflict Generic"));
	}

	private TestCase createTypeSystemPropertiesTest(String name) {
		final StringIdRoundTripTableElement element = new StringIdRoundTripTableElement(true);
		element.id = UUID.randomUUID().toString();

		TestCase roundtripTest = new TestCase() {

			@Override
			protected void executeTest(final MobileServiceClient client, final TestExecutionCallback callback) {
				final TestCase test = this;

				executeTask(new AsyncTask<Void, Void, Void>() {
					@Override
					protected Void doInBackground(Void... params) {
						TestResult result = new TestResult();
						result.setTestCase(test);

						try {
							MobileServiceTable<StringIdRoundTripTableElement> table = client
									.getTable(STRING_ID_TABLE_NAME, StringIdRoundTripTableElement.class);

							log("Insert item - " + element.toString());
							final StringIdRoundTripTableElement responseElement1 = insert(table, element);

							log("Verify system properties are not null");
							verifySystemProperties("Insert response", responseElement1);

							log("Read table");
							List<StringIdRoundTripTableElement> responseElements2 = read(table);

							List<StringIdRoundTripTableElement> filteredResponseElements = filter(responseElements2,
									new IPredicate<StringIdRoundTripTableElement>() {
										@Override
										public boolean evaluate(StringIdRoundTripTableElement type) {
											return type.id.equals(responseElement1.id);
										}
									});

							log("Verify previously inserted item is included in the response");
							if (filteredResponseElements == null || filteredResponseElements.size() == 0) {
								throw new Exception("Read response - Missing previously inserted element");
							}

							StringIdRoundTripTableElement responseElement2 = filteredResponseElements.get(0);

							log("Verify system properties are not null");
							verifySystemProperties("Read response", responseElement2);

							final String versionFilter = responseElement1.Version;

							List<StringIdRoundTripTableElement> filteredVersionResponseElements = filter(responseElements2,
									new IPredicate<StringIdRoundTripTableElement>() {
										@Override
										public boolean evaluate(StringIdRoundTripTableElement element) {
											return element.Version.equals(versionFilter);
										}
									});

							log("Filter table - Version");
							List<StringIdRoundTripTableElement> filteredVersionElements = read(table, field("__version").eq().val(versionFilter));

							log("Verify response size");
							if (filteredVersionElements == null || filteredVersionElements.size() != filteredVersionResponseElements.size()) {
								throw new Exception("Filter response - Version - incorrect number of records");
							}

							log("Verify system properties are not null");
							for (StringIdRoundTripTableElement filteredVersionElement : filteredVersionElements) {
								if (filteredVersionElement.Version == null) {
									throw new Exception("Filter response - Version is null");
								} else if (!filteredVersionElement.Version.equals(versionFilter)) {
									throw new ExpectedValueException(versionFilter, filteredVersionElement.Version);
								}
							}

							final Date createdAtFilter = responseElement1.CreatedAt;

							List<StringIdRoundTripTableElement> filteredCreatedAtResponseElements = filter(responseElements2,
									new IPredicate<StringIdRoundTripTableElement>() {
										@Override
										public boolean evaluate(StringIdRoundTripTableElement element) {
											return element.CreatedAt.equals(createdAtFilter);
										}
									});

							log("Filter table - CreatedAt");
							List<StringIdRoundTripTableElement> filteredCreatedAtElements = read(table, field("__createdAt").eq().val(createdAtFilter));

							log("verify response size");
							if (filteredCreatedAtElements == null || filteredCreatedAtElements.size() != filteredCreatedAtResponseElements.size()) {
								throw new Exception("Filter response - CreatedAt - incorrect number of records");
							}

							log("verify system properties are not null");
							for (StringIdRoundTripTableElement filteredCreatedAtElement : filteredCreatedAtElements) {
								if (filteredCreatedAtElement.CreatedAt == null) {
									throw new Exception("Filter response - CreatedAt is null");
								} else if (!filteredCreatedAtElement.CreatedAt.equals(createdAtFilter)) {
									throw new ExpectedValueException(createdAtFilter, filteredCreatedAtElement.CreatedAt);
								}
							}

							final Date updatedAtFilter = responseElement1.UpdatedAt;

							List<StringIdRoundTripTableElement> filteredUpdatedAtResponseElements = filter(responseElements2,
									new IPredicate<StringIdRoundTripTableElement>() {
										@Override
										public boolean evaluate(StringIdRoundTripTableElement element) {
											return element.UpdatedAt.equals(updatedAtFilter);
										}
									});

							log("Filter table - UpdatedAt");
							List<StringIdRoundTripTableElement> filteredUpdatedAtElements = read(table, field("__updatedAt").eq().val(updatedAtFilter));

							log("verify response size");
							if (filteredUpdatedAtElements == null || filteredUpdatedAtElements.size() != filteredUpdatedAtResponseElements.size()) {
								throw new Exception("Filter response - UpdatedAt - incorrect number of records");
							}

							log("verify system properties are not null");
							for (StringIdRoundTripTableElement filteredUpdatedAtElement : filteredUpdatedAtElements) {
								if (filteredUpdatedAtElement.UpdatedAt == null) {
									throw new Exception("Filter response - UpdatedAt is null");
								} else if (!filteredUpdatedAtElement.UpdatedAt.equals(updatedAtFilter)) {
									throw new ExpectedValueException(updatedAtFilter, filteredUpdatedAtElement.UpdatedAt);
								}
							}

							String lookUpId = responseElement1.id;

							log("LookUp");
							StringIdRoundTripTableElement lookUpElement = lookUp(table, lookUpId);

							log("verify element is not null");
							if (lookUpElement == null) {
								throw new Exception("LookUp response - Element is null");
							}

							if (!compare(responseElement1.id, lookUpElement.id)) {
								throw new ExpectedValueException(responseElement1.id, lookUpElement.id);
							}

							if (!compare(responseElement1.Version, lookUpElement.Version)) {
								throw new ExpectedValueException(responseElement1.Version, lookUpElement.Version);
							}

							if (!compare(responseElement1.CreatedAt, lookUpElement.CreatedAt)) {
								throw new ExpectedValueException(responseElement1.CreatedAt, lookUpElement.CreatedAt);
							}

							if (!compare(responseElement1.UpdatedAt, lookUpElement.UpdatedAt)) {
								throw new ExpectedValueException(responseElement1.UpdatedAt, lookUpElement.UpdatedAt);
							}

							StringIdRoundTripTableElement updateElement = new StringIdRoundTripTableElement(responseElement1);
							updateElement.name = "Other Sample Data";

							log("Update");
							updateElement = update(table, updateElement);

							log("Verify element is not null");
							if (updateElement == null) {
								throw new Exception("Update response - Element is null");
							}

							if (!compare(responseElement1.id, updateElement.id)) {
								throw new ExpectedValueException(responseElement1.id, updateElement.id);
							}

							if (compare(responseElement1.Version, updateElement.Version)) {
								throw new Exception("Update response - same Version");
							}

							if (!compare(responseElement1.CreatedAt, updateElement.CreatedAt)) {
								throw new ExpectedValueException(responseElement1.CreatedAt, updateElement.CreatedAt);
							}

							if (!responseElement1.UpdatedAt.before(updateElement.UpdatedAt)) {
								throw new Exception("Update response - incorrect UpdatedAt");
							}

							log("Delete element");
							delete(table, responseElement1);

							result.setStatus(TestStatus.Passed);
							if (callback != null) {
								callback.onTestComplete(test, result);
							}
						} catch (Exception ex) {
							result = createResultFromException(result, ex);

							if (callback != null) {
								callback.onTestComplete(test, result);
							}
						}

						return null;
					}
				});
			}
		};

		roundtripTest.setName(name);
		return roundtripTest;
	}

	private TestCase createCustomSystemPropertiesTest(String name) {
		TestCase roundtripTest = new TestCase() {

			@Override
			protected void executeTest(final MobileServiceClient client, final TestExecutionCallback callback) {
				final TestCase test = this;

				executeTask(new AsyncTask<Void, Void, Void>() {
					@Override
					protected Void doInBackground(Void... params) {
						TestResult result = new TestResult();
						result.setTestCase(test);

						try {
							MobileServiceTable<StringIdRoundTripTableElement> table = client
									.getTable(STRING_ID_TABLE_NAME, StringIdRoundTripTableElement.class);

							StringIdRoundTripTableElement element1 = new StringIdRoundTripTableElement(true);
							element1.id = UUID.randomUUID().toString();

							log("Insert element 1 with Type System Properties - " + element1.toString());
							StringIdRoundTripTableElement responseElement1 = insert(table, element1);

							log("Verify system properties are not null");
							verifySystemProperties("Insert response", responseElement1);

							StringIdRoundTripTableElement element2 = new StringIdRoundTripTableElement(true);
							element2.id = UUID.randomUUID().toString();

							EnumSet<MobileServiceSystemProperty> systemProperties2 = EnumSet.noneOf(MobileServiceSystemProperty.class);
							systemProperties2.add(MobileServiceSystemProperty.Version);
							systemProperties2.add(MobileServiceSystemProperty.CreatedAt);

							table.setSystemProperties(systemProperties2);

							log("Insert element 2 with Custom System Properties - Version|CreatedAt - " + element2.toString());
							StringIdRoundTripTableElement responseElement2 = insert(table, element2);

							log("Verify Version|CreatedAt System Properties are not null, and UpdateAt is null or default");
							verifySystemProperties("Insert response", true, false, true, responseElement2);

							EnumSet<MobileServiceSystemProperty> systemProperties3 = EnumSet.noneOf(MobileServiceSystemProperty.class);
							systemProperties3.add(MobileServiceSystemProperty.Version);
							systemProperties3.add(MobileServiceSystemProperty.UpdatedAt);

							table.setSystemProperties(systemProperties3);

							log("Filter element2 id with Custom System Properties - Version|UpdatedAt");
							List<StringIdRoundTripTableElement> responseElements3 = read(table, field("id").eq().val(element2.id));

							log("Verify response size");
							if (responseElements3 == null || responseElements3.size() != 1) {
								throw new Exception("Read response - incorrect number of records");
							}

							StringIdRoundTripTableElement responseElement3 = responseElements3.get(0);

							log("Verify Version|UpdatedAt System Properties are not null, and CreatedAt is null or default");
							verifySystemProperties("Read response", false, true, true, responseElement3);

							EnumSet<MobileServiceSystemProperty> systemProperties4 = EnumSet.noneOf(MobileServiceSystemProperty.class);

							table.setSystemProperties(systemProperties4);

							log("Lookup element2 id with No System Properties");
							StringIdRoundTripTableElement responseElement4 = lookUp(table, element2.id);

							log("Verify Version|CreatedAt|UpdatedAt System Properties are null");
							verifySystemProperties("Read response", false, false, false, responseElement4);

							log("Delete element");
							delete(table, responseElement1);

							result.setStatus(TestStatus.Passed);
							if (callback != null) {
								callback.onTestComplete(test, result);
							}
						} catch (Exception ex) {
							result = createResultFromException(result, ex);

							if (callback != null) {
								callback.onTestComplete(test, result);
							}
						}

						return null;
					}
				});
			}
		};

		roundtripTest.setName(name);
		return roundtripTest;
	}

	private TestCase createQueryParameterSystemPropertiesTest(String name, final String systemProperties) {
		TestCase roundtripTest = new TestCase() {

			@Override
			protected void executeTest(final MobileServiceClient client, final TestExecutionCallback callback) {
				final TestCase test = this;

				executeTask(new AsyncTask<Void, Void, Void>() {
					@Override
					protected Void doInBackground(Void... params) {
						TestResult result = new TestResult();
						result.setTestCase(test);

						try {
							MobileServiceTable<StringIdRoundTripTableElement> table = client
									.getTable(STRING_ID_TABLE_NAME, StringIdRoundTripTableElement.class);

							String[] systemPropertiesKeyValue = systemProperties.split("=");
							String key = systemPropertiesKeyValue[0];
							String value = systemPropertiesKeyValue[1];
							List<Pair<String, String>> userParameters = new ArrayList<Pair<String, String>>();
							userParameters.add(new Pair<String, String>(key, value));

							boolean shouldHaveCreatedAt = value.toLowerCase(Locale.getDefault()).contains("created");
							boolean shouldHaveUpdatedAt = value.toLowerCase(Locale.getDefault()).contains("updated");
							boolean shouldHaveVersion = value.toLowerCase(Locale.getDefault()).contains("version");

							if (value.trim().equals("*")) {
								shouldHaveVersion = shouldHaveUpdatedAt = shouldHaveCreatedAt = true;
							}

							StringIdRoundTripTableElement element1 = new StringIdRoundTripTableElement(true);
							element1.id = UUID.randomUUID().toString();

							log("Insert element 1 with Query Parameter System Properties - " + element1.toString() + " - " + systemProperties);
							final StringIdRoundTripTableElement responseElement1 = insert(table, element1, userParameters);

							log("Verify Query Parameter System Properties");
							verifySystemProperties("Insert Response", shouldHaveCreatedAt, shouldHaveUpdatedAt, shouldHaveVersion, responseElement1);

							log("Read with Query Parameter System Properties - " + systemProperties);
							List<StringIdRoundTripTableElement> responseElements2 = read(table, userParameters);

							List<StringIdRoundTripTableElement> filteredResponseElements = filter(responseElements2,
									new IPredicate<StringIdRoundTripTableElement>() {
										@Override
										public boolean evaluate(StringIdRoundTripTableElement type) {
											return type.id.equals(responseElement1.id);
										}
									});

							log("Verify previously inserted item is included in the response");
							if (filteredResponseElements == null || filteredResponseElements.size() == 0) {
								throw new Exception("Read response - Missing previously inserted element");
							}

							StringIdRoundTripTableElement responseElement2 = filteredResponseElements.get(0);

							log("Verify Query Parameter System Properties");
							verifySystemProperties("Read Response", shouldHaveCreatedAt, shouldHaveUpdatedAt, shouldHaveVersion, responseElement2);

							log("Filter element1 id with Query Parameter System Properties - " + systemProperties);
							List<StringIdRoundTripTableElement> responseElements3 = read(table, field("id").eq().val(element1.id), userParameters);

							log("Verify response size");
							if (responseElements3 == null || responseElements3.size() != 1) {
								throw new Exception("Filter response - incorrect number of records");
							}

							StringIdRoundTripTableElement responseElement3 = responseElements3.get(0);

							log("Verify Query Parameter System Properties");
							verifySystemProperties("Filter Response", shouldHaveCreatedAt, shouldHaveUpdatedAt, shouldHaveVersion, responseElement3);

							log("Lookup element1 id with Query Parameter System Properties - " + systemProperties);
							StringIdRoundTripTableElement responseElement4 = lookUp(table, element1.id, userParameters);

							log("Verify Query Parameter System Properties");
							verifySystemProperties("Lookup Response", shouldHaveCreatedAt, shouldHaveUpdatedAt, shouldHaveVersion, responseElement4);

							StringIdRoundTripTableElement updateElement1 = new StringIdRoundTripTableElement(element1);
							updateElement1.name = "Other Sample Data";

							log("Update element1 with Query Parameter System Properties - " + updateElement1.toString() + " - " + systemProperties);
							StringIdRoundTripTableElement responseElement5 = update(table, updateElement1, userParameters);

							log("Verify Query Parameter System Properties");
							verifySystemProperties("Update Response", shouldHaveCreatedAt, shouldHaveUpdatedAt, shouldHaveVersion, responseElement5);

							log("Delete element");
							delete(table, responseElement1);

							result.setStatus(TestStatus.Passed);
							if (callback != null) {
								callback.onTestComplete(test, result);
							}
						} catch (Exception ex) {
							result = createResultFromException(result, ex);

							if (callback != null) {
								callback.onTestComplete(test, result);
							}
						}

						return null;
					}
				});
			}
		};

		roundtripTest.setName(name);
		return roundtripTest;
	}

	private TestCase createMergeConflictTest(String name) {
		TestCase roundtripTest = new TestCase() {

			@Override
			protected void executeTest(final MobileServiceClient client, final TestExecutionCallback callback) {
				final TestCase test = this;

				executeTask(new AsyncTask<Void, Void, Void>() {
					@Override
					protected Void doInBackground(Void... params) {
						TestResult result = new TestResult();
						result.setTestCase(test);

						JsonObject responseJsonElement1 = null;

						try {
							MobileServiceJsonTable jsonTable = client.getTable(STRING_ID_TABLE_NAME);

							EnumSet<MobileServiceSystemProperty> systemProperties = EnumSet.noneOf(MobileServiceSystemProperty.class);
							systemProperties.add(MobileServiceSystemProperty.Version);

							jsonTable.setSystemProperties(systemProperties);

							StringIdJsonElement element1 = new StringIdJsonElement(true);

							JsonObject jsonElement1 = client.getGsonBuilder().create().toJsonTree(element1).getAsJsonObject();

							log("Insert Json element 1 - " + jsonElement1.toString());
							responseJsonElement1 = insert(jsonTable, jsonElement1);

							JsonObject responseJsonElement1Copy = new JsonParser().parse(responseJsonElement1.toString()).getAsJsonObject();

							responseJsonElement1Copy.remove("__version");
							responseJsonElement1Copy.addProperty("__version", "random");

							log("Update response Json element 1 copy - " + responseJsonElement1Copy.toString());
							update(jsonTable, responseJsonElement1Copy);

							result.setStatus(TestStatus.Failed);
							if (callback != null) {
								callback.onTestComplete(test, result);
							}
						} catch (Exception ex) {
							if (ex instanceof MobileServicePreconditionFailedExceptionBase) {
								MobileServicePreconditionFailedExceptionBase preconditionFailed = (MobileServicePreconditionFailedExceptionBase) ex;
								JsonObject serverValue = preconditionFailed.getValue();

								String serverVersion = serverValue.get("__version").getAsString();
								String responseVersion = responseJsonElement1.get("__version").getAsString();

								if (!serverVersion.equals(responseVersion)) {
									ex = new ExpectedValueException(serverVersion, responseVersion);
								}
							}

							result = createResultFromException(result, ex);

							if (callback != null) {
								callback.onTestComplete(test, result);
							}
						}

						return null;
					};
				});
			}
		};

		roundtripTest.setExpectedExceptionClass(MobileServicePreconditionFailedExceptionBase.class);
		roundtripTest.setName(name);
		return roundtripTest;
	}

	private TestCase createMergeConflictGenericTest(String name) {
		TestCase roundtripTest = new TestCase() {

			@Override
			protected void executeTest(final MobileServiceClient client, final TestExecutionCallback callback) {
				final TestCase test = this;

				executeTask(new AsyncTask<Void, Void, Void>() {
					@Override
					protected Void doInBackground(Void... params) {
						TestResult result = new TestResult();
						result.setTestCase(test);

						StringIdRoundTripTableElement responseElement1 = null;

						try {
							MobileServiceTable<StringIdRoundTripTableElement> table = client
									.getTable(STRING_ID_TABLE_NAME, StringIdRoundTripTableElement.class);

							StringIdRoundTripTableElement element1 = new StringIdRoundTripTableElement(true);

							log("Insert element 1 - " + element1.toString());
							responseElement1 = insert(table, element1);

							StringIdRoundTripTableElement responseElement1Copy = new StringIdRoundTripTableElement(responseElement1);

							responseElement1Copy.Version = "random";

							log("Update response element 1 copy - " + responseElement1Copy.toString());
							update(table, responseElement1Copy);

							result.setStatus(TestStatus.Failed);
							if (callback != null) {
								callback.onTestComplete(test, result);
							}
						} catch (Exception ex) {
							if (ex instanceof MobileServicePreconditionFailedException) {
								MobileServicePreconditionFailedException preconditionFailed = (MobileServicePreconditionFailedException) ex;
								StringIdRoundTripTableElement serverValue = (StringIdRoundTripTableElement) preconditionFailed.getItem();

								String serverVersion = serverValue.Version;
								String responseVersion = responseElement1.Version;

								if (!serverVersion.equals(responseVersion)) {
									ex = new ExpectedValueException(serverVersion, responseVersion);
								}
							}

							result = createResultFromException(result, ex);

							if (callback != null) {
								callback.onTestComplete(test, result);
							}
						}

						return null;
					}
				});
			}
		};

		roundtripTest.setExpectedExceptionClass(MobileServicePreconditionFailedException.class);
		roundtripTest.setName(name);
		return roundtripTest;
	}

	private <T> List<T> read(final MobileServiceTable<T> table) throws Exception {
		return read(table, null, null);
	}

	private <T> List<T> read(final MobileServiceTable<T> table, final List<Pair<String, String>> parameters) throws Exception {
		return read(table, null, parameters);
	}

	private <T> List<T> read(final MobileServiceTable<T> table, final MobileServiceQuery<?> filter) throws Exception {
		return read(table, filter, null);
	}

	private <T> List<T> read(final MobileServiceTable<T> table, final MobileServiceQuery<?> filter, final List<Pair<String, String>> parameters)
			throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		final ResultsContainer<T> container = new ResultsContainer<T>();

		MobileServiceQuery<TableQueryCallback<T>> query;

		if (filter != null) {
			query = table.where(filter);
		} else {
			query = table.where();
		}

		if (parameters != null) {
			for (Pair<String, String> parameter : parameters) {
				query.parameter(parameter.first, parameter.second);
			}
		}

		table.execute(query, new TableQueryCallback<T>() {

			@Override
			public void onCompleted(List<T> responseElements, int count, Exception executeException, ServiceFilterResponse response) {
				if (executeException == null) {
					container.setItems(responseElements);
				} else {
					container.setException(executeException);
				}

				latch.countDown();
			}
		});

		latch.await();

		Exception ex = container.getException();

		if (ex != null) {
			throw ex;
		} else {
			return container.getItems();
		}
	}

	private <T> T lookUp(final MobileServiceTable<T> table, final Object id) throws Exception {
		return lookUp(table, id, null);
	}

	private <T> T lookUp(final MobileServiceTable<T> table, final Object id, final List<Pair<String, String>> parameters) throws Exception {
		final ResultsContainer<T> container = new ResultsContainer<T>();
		final CountDownLatch latch = new CountDownLatch(1);

		table.lookUp(id, parameters, new TableOperationCallback<T>() {

			@Override
			public void onCompleted(T responseElement, Exception exception, ServiceFilterResponse response) {
				if (exception == null) {
					container.setItem(responseElement);
				} else {
					container.setException(exception);
				}

				latch.countDown();
			}
		});

		latch.await();

		Exception ex = container.getException();

		if (ex != null) {
			throw ex;
		} else {
			return container.getItem();
		}
	}

	private <T> T insert(final MobileServiceTable<T> table, final T element) throws Exception {
		return insert(table, element, null);
	}

	private <T> T insert(final MobileServiceTable<T> table, final T element, final List<Pair<String, String>> parameters) throws Exception {
		final ResultsContainer<T> container = new ResultsContainer<T>();
		final CountDownLatch latch = new CountDownLatch(1);

		table.insert(element, parameters, new TableOperationCallback<T>() {

			@Override
			public void onCompleted(T responseElement, Exception exception, ServiceFilterResponse response) {
				if (exception == null) {
					container.setItem(responseElement);
				} else {
					container.setException(exception);
				}

				latch.countDown();
			}
		});

		latch.await();

		Exception ex = container.getException();

		if (ex != null) {
			throw ex;
		} else {
			return container.getItem();
		}
	}

	private JsonObject insert(final MobileServiceJsonTable table, final JsonObject element) throws Exception {
		return insert(table, element, null);
	}

	private JsonObject insert(final MobileServiceJsonTable table, final JsonObject element, final List<Pair<String, String>> parameters) throws Exception {
		final ResultsContainer<JsonObject> container = new ResultsContainer<JsonObject>();
		final CountDownLatch latch = new CountDownLatch(1);

		table.insert(element, parameters, new TableJsonOperationCallback() {

			@Override
			public void onCompleted(JsonObject jsonObject, Exception exception, ServiceFilterResponse response) {
				if (exception == null) {
					container.setItem(jsonObject);
				} else {
					container.setException(exception);
				}

				latch.countDown();
			}
		});

		latch.await();

		Exception ex = container.getException();

		if (ex != null) {
			throw ex;
		} else {
			return container.getItem();
		}
	}

	private <T> T update(final MobileServiceTable<T> table, final T element) throws Exception {
		return update(table, element, null);
	}

	private <T> T update(final MobileServiceTable<T> table, final T element, final List<Pair<String, String>> parameters) throws Exception {
		final ResultsContainer<T> container = new ResultsContainer<T>();
		final CountDownLatch latch = new CountDownLatch(1);

		table.update(element, parameters, new TableOperationCallback<T>() {

			@Override
			public void onCompleted(T responseElement, Exception exception, ServiceFilterResponse response) {
				if (exception == null) {
					container.setItem(responseElement);
				} else {
					container.setException(exception);
				}

				latch.countDown();
			}
		});

		latch.await();

		Exception ex = container.getException();

		if (ex != null) {
			throw ex;
		} else {
			return container.getItem();
		}
	}

	private JsonObject update(final MobileServiceJsonTable table, final JsonObject element) throws Exception {
		return update(table, element, null);
	}

	private JsonObject update(final MobileServiceJsonTable table, final JsonObject element, final List<Pair<String, String>> parameters) throws Exception {
		final ResultsContainer<JsonObject> container = new ResultsContainer<JsonObject>();
		final CountDownLatch latch = new CountDownLatch(1);

		table.update(element, parameters, new TableJsonOperationCallback() {

			@Override
			public void onCompleted(JsonObject jsonObject, Exception exception, ServiceFilterResponse response) {
				if (exception == null) {
					container.setItem(jsonObject);
				} else {
					container.setException(exception);
				}

				latch.countDown();
			}
		});

		latch.await();

		Exception ex = container.getException();

		if (ex != null) {
			throw ex;
		} else {
			return container.getItem();
		}
	}

	private <T> void delete(final MobileServiceTable<T> table, final T element) throws Exception {
		final ResultsContainer<T> container = new ResultsContainer<T>();
		final CountDownLatch latch = new CountDownLatch(1);

		table.delete(element, new TableDeleteCallback() {

			@Override
			public void onCompleted(Exception exception, ServiceFilterResponse response) {
				if (exception != null) {
					container.setException(exception);
				}

				latch.countDown();
			}
		});

		latch.await();

		Exception ex = container.getException();

		if (ex != null) {
			throw ex;
		}
	}

	@SuppressLint("NewApi")
	private void executeTask(AsyncTask<Void, Void, Void> task) {
		// If it's running with Honeycomb or greater, it must execute each
		// request in a different thread
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		} else {
			task.execute();
		}
	}

	private void verifySystemProperties(String message, StringIdRoundTripTableElement element) throws Exception {
		verifySystemProperties(message, true, true, true, element);
	}

	private void verifySystemProperties(String message, boolean shouldHaveCreatedAt, boolean shouldHaveUpdatedAt, boolean shouldHaveVersion,
			StringIdRoundTripTableElement element) throws Exception {
		if ((shouldHaveCreatedAt && element.CreatedAt == null) || (!shouldHaveCreatedAt && element.CreatedAt != null)
				|| (shouldHaveUpdatedAt && element.UpdatedAt == null) || (!shouldHaveUpdatedAt && element.UpdatedAt != null)
				|| (shouldHaveVersion && element.Version == null) || (!shouldHaveVersion && element.Version != null)) {
			StringBuilder builder = new StringBuilder();
			builder.append(message);
			builder.append(" - System Properties");

			if (shouldHaveCreatedAt && element.CreatedAt == null) {
				builder.append(" - CreatedAt is null");
			} else if (!shouldHaveCreatedAt && element.CreatedAt != null) {
				builder.append(" - CreatedAt is not null");
			}

			if (shouldHaveUpdatedAt && element.UpdatedAt == null) {
				builder.append(" - UpdatedAt is null");
			} else if (!shouldHaveUpdatedAt && element.UpdatedAt != null) {
				builder.append(" - UpdatedAt is not null");
			}

			if (shouldHaveVersion && element.Version == null) {
				builder.append(" - Version is null");
			} else if (!shouldHaveVersion && element.Version != null) {
				builder.append(" - Version is not null");
			}

			throw new Exception(builder.toString());
		}
	}
}
