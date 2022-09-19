# LOCASSC: Location-Aware Scalable Service Composition

The problem of service composition is the process of assigning resources to services from a pool of available ones in
the shortest possible time so that the overall Quality of Service is maximized.

We are working on solutions for the composition problem that takes into account its scalability, services' locations,
and users' restrictions, which are key for the management of applications using state-of-the-art technologies.

The provided solutions use different techniques, including genetic algorithms and heuristics.

We provide an extensive experimental evaluation, which shows the pros and cons of each of them, and allows us to
characterize the preferred solution for each specific application.

# The Problem

Automatic service composition has turned key in the development of enterprise architectures for distributed systems.

Technologies such as the Cloud, the Internet of Things (IoT), or the Cloud-Fog-Edge continuum have brought new
challenges into the picture:

(1) Applications of hundreds, even thousands of services are being considered, and managing them manually has become
impractical;

(2) functionally equivalent services, with different Quality of Service (QoS) attributes, may be traded in services
repositories, with the goal of optimizing the overall QoS, at a minimum cost, but the available offer makes the decision
of where to deploy each service much harder; and

(3) the distances between the components of an application, and between the front-end services and the users of the
applications, as well as the quality of the communication channels being used, are key for providing the appropriate
response times to the users, which in the context of service providers located around the world may suppose a
significant difference.

For us, the problem of service composition is the process of assigning resources to services from a pool of available
ones.

From all possible assignments, we are interested in finding the best possible solution, meaning that the overall QoS is
optimal, or pseudo-optimal, in the shortest possible time, and all users' preferences (soft and hard) are satisfied.

Given the above mentioned scenario, we provide solutions for the composition problem that takes into account its
scalability, services' locations, and users' restrictions.

We assume that:

- The architecture of the service-based application to be composed will be provided as input, so that, not only service
  descriptions and constraints are considered, but also explicit functional dependencies between services.

  We assume a workflow description formalism like WS-BPEL.


- For each service, there will be an offer of candidate service providers, each with an estimate of its QoS attributes,
  specified as its Service Level Agreement (SLA), which will be taken into account to calculate the solution.

To be able to consider communications' quality, the location and connection capacity of the providers will be as well
available.

# The Approach

- We exploit the combination of GA, direct methods, and utility-based heuristics.

- Our solutions consider the usual QoS attributes, namely cost, execution time, reliability, and availability, but also
  provides novel proposals to take into account channel-dependent attributes like latency and throughput.

- Some of these solutions are able to handle constraints (GA, U and UM), and others do not (Express).

- The solution of the composition will be guided by the so-called *fitness* or *objective* functions, as a measure of
  the global aggregated QoS, which will be used to quantifying the quality of a solution for a given composition.

Experimental results and a discussion on the selection of hyper-parameters is available at 
**http://services-composition-web.s3-website-us-east-1.amazonaws.com/**.

# Project structure

```bash
├── executions
│   ├── App.java
│   └── Main.java
├── generators
├── models
│   ├── analyzers
│   ├── applications
│   ├── auxiliary
│   ├── enums
│   ├── geo
│   └── patterns
├── problems
├── resolvers
└── utils
```

The main execution of this project is in the file `executions -> Main.java`, this file will execute the different methods and algorithms, and will dump all the output information in the folder called `data`. 

In the `generators` folder, you will find classes with methods that return random applications, random services, random providers, etc. This is intended to be used for random testing and to get statistics on the results.

If we continue through the `models` folder, we will find many sub-folders where the application models are defined, enumerated, architectural patterns, among other elements.

The next two folders, `problems` and `resolvers`, are the fundamental folders of the project. In the first one, `problems`, the problems to be solved for the different methods are defined. In these classes, the definition of the genotype for the genetic algorithm, for the utility, etc. is found. In the other folder called `resolvers` are the classes designed to solve the problems defined above, among the solvers we find the solver of the algorithm `Express`, `GA`, `Random`, `Utility` and `Utility Modified`.

Finally, we have a folder called `utils`, with various functions for working with directors, data outputs, CSV files, among other elements, CSV files, among other elements.
