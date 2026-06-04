package be.elchworks.testdatagenerator;

record Person(String name, int age) {

    static Builder builder() {
        return new Builder();
    }

    static class Builder {
        private Builder() {
        }

        private String name;
        private int age;

        Builder name(String name) {
            this.name = name;
            return this;
        }

        Builder age(int age) {
            this.age = age;
            return this;
        }

        Person build() {
            return new Person(name, age);
        }

        String toJson() {
            return Json.toJson(build());
        }
    }
}
