import java.sql.*;
import java.util.*;

// Database connection utility class
class DatabaseConnection {
    private static final String URL = "jdbc:sqlite:school.db";
    private static Connection connection;
    
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(URL);
        }
        return connection;
    }
    
    public static void initializeDatabase() throws SQLException {
        Connection conn = getConnection();
        Statement stmt = conn.createStatement();
        
        // Create tables
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS students (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                gpa REAL NOT NULL
            )
        """);
        
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS teachers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """);
        
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS class_sections (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                length INTEGER NOT NULL,
                teacher_id INTEGER,
                FOREIGN KEY (teacher_id) REFERENCES teachers(id)
            )
        """);
        
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS teacher_sections (
                teacher_id INTEGER,
                section_id INTEGER,
                PRIMARY KEY (teacher_id, section_id),
                FOREIGN KEY (teacher_id) REFERENCES teachers(id),
                FOREIGN KEY (section_id) REFERENCES class_sections(id)
            )
        """);
        
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS section_students (
                section_id INTEGER,
                student_id INTEGER,
                PRIMARY KEY (section_id, student_id),
                FOREIGN KEY (section_id) REFERENCES class_sections(id),
                FOREIGN KEY (student_id) REFERENCES students(id)
            )
        """);
        
        stmt.close();
    }
}

// Student class
class Student {
    private int id;
    private String name;
    private double gpa;
    
    public Student(String name, double gpa) {
        this.name = name;
        this.gpa = gpa;
    }
    
    public Student(int id, String name, double gpa) {
        this.id = id;
        this.name = name;
        this.gpa = gpa;
    }
    
    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getGpa() { return gpa; }
    public void setGpa(double gpa) { this.gpa = gpa; }
    
    // Database operations
    public void save() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        if (id == 0) {
            // Insert new student
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO students (name, gpa) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            stmt.setString(1, name);
            stmt.setDouble(2, gpa);
            stmt.executeUpdate();
            
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                this.id = keys.getInt(1);
            }
            stmt.close();
        } else {
            // Update existing student
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE students SET name = ?, gpa = ? WHERE id = ?"
            );
            stmt.setString(1, name);
            stmt.setDouble(2, gpa);
            stmt.setInt(3, id);
            stmt.executeUpdate();
            stmt.close();
        }
    }
    
    public static Student findById(int id) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM students WHERE id = ?");
        stmt.setInt(1, id);
        ResultSet rs = stmt.executeQuery();
        
        Student student = null;
        if (rs.next()) {
            student = new Student(rs.getInt("id"), rs.getString("name"), rs.getDouble("gpa"));
        }
        
        stmt.close();
        return student;
    }
    
    public static List<Student> findAll() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM students");
        
        List<Student> students = new ArrayList<>();
        while (rs.next()) {
            students.add(new Student(rs.getInt("id"), rs.getString("name"), rs.getDouble("gpa")));
        }
        
        stmt.close();
        return students;
    }
    
    public void delete() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement("DELETE FROM students WHERE id = ?");
        stmt.setInt(1, id);
        stmt.executeUpdate();
        stmt.close();
    }
    
    @Override
    public String toString() {
        return String.format("Student{id=%d, name='%s', gpa=%.2f}", id, name, gpa);
    }
}

// Teacher class
class Teacher {
    private int id;
    private String name;
    private List<ClassSection> classSections;
    
    public Teacher(String name) {
        this.name = name;
        this.classSections = new ArrayList<>();
    }
    
    public Teacher(int id, String name) {
        this.id = id;
        this.name = name;
        this.classSections = new ArrayList<>();
    }
    
    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<ClassSection> getClassSections() { return classSections; }
    
    public void addClassSection(ClassSection section) {
        if (!classSections.contains(section)) {
            classSections.add(section);
        }
    }
    
    public void removeClassSection(ClassSection section) {
        classSections.remove(section);
    }
    
    // Database operations
    public void save() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        if (id == 0) {
            // Insert new teacher
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO teachers (name) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS
            );
            stmt.setString(1, name);
            stmt.executeUpdate();
            
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                this.id = keys.getInt(1);
            }
            stmt.close();
        } else {
            // Update existing teacher
            PreparedStatement stmt = conn.prepareStatement("UPDATE teachers SET name = ? WHERE id = ?");
            stmt.setString(1, name);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            stmt.close();
        }
        
        // Save teacher-section relationships
        saveTeacherSections();
    }
    
    private void saveTeacherSections() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        // Clear existing relationships
        PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM teacher_sections WHERE teacher_id = ?");
        deleteStmt.setInt(1, id);
        deleteStmt.executeUpdate();
        deleteStmt.close();
        
        // Insert new relationships
        PreparedStatement insertStmt = conn.prepareStatement(
            "INSERT INTO teacher_sections (teacher_id, section_id) VALUES (?, ?)"
        );
        for (ClassSection section : classSections) {
            insertStmt.setInt(1, id);
            insertStmt.setInt(2, section.getId());
            insertStmt.executeUpdate();
        }
        insertStmt.close();
    }
    
    public static Teacher findById(int id) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM teachers WHERE id = ?");
        stmt.setInt(1, id);
        ResultSet rs = stmt.executeQuery();
        
        Teacher teacher = null;
        if (rs.next()) {
            teacher = new Teacher(rs.getInt("id"), rs.getString("name"));
            teacher.loadClassSections();
        }
        
        stmt.close();
        return teacher;
    }
    
    private void loadClassSections() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement("""
            SELECT cs.* FROM class_sections cs 
            JOIN teacher_sections ts ON cs.id = ts.section_id 
            WHERE ts.teacher_id = ?
        """);
        stmt.setInt(1, id);
        ResultSet rs = stmt.executeQuery();
        
        while (rs.next()) {
            ClassSection section = new ClassSection(
                rs.getInt("id"), 
                rs.getInt("length")
            );
            section.loadStudents();
            this.classSections.add(section);
        }
        
        stmt.close();
    }
    
    public static List<Teacher> findAll() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM teachers");
        
        List<Teacher> teachers = new ArrayList<>();
        while (rs.next()) {
            Teacher teacher = new Teacher(rs.getInt("id"), rs.getString("name"));
            teacher.loadClassSections();
            teachers.add(teacher);
        }
        
        stmt.close();
        return teachers;
    }
    
    public void delete() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement("DELETE FROM teachers WHERE id = ?");
        stmt.setInt(1, id);
        stmt.executeUpdate();
        stmt.close();
    }
    
    @Override
    public String toString() {
        return String.format("Teacher{id=%d, name='%s', sections=%d}", id, name, classSections.size());
    }
}

// ClassSection class
class ClassSection {
    private int id;
    private int length;
    private List<Student> students;
    
    public ClassSection(int length) {
        this.length = length;
        this.students = new ArrayList<>();
    }
    
    public ClassSection(int id, int length) {
        this.id = id;
        this.length = length;
        this.students = new ArrayList<>();
    }
    
    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    public List<Student> getStudents() { return students; }
    
    public void addStudent(Student student) {
        if (!students.contains(student)) {
            students.add(student);
        }
    }
    
    public void removeStudent(Student student) {
        students.remove(student);
    }
    
    // Database operations
    public void save() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        if (id == 0) {
            // Insert new section
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO class_sections (length) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS
            );
            stmt.setInt(1, length);
            stmt.executeUpdate();
            
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                this.id = keys.getInt(1);
            }
            stmt.close();
        } else {
            // Update existing section
            PreparedStatement stmt = conn.prepareStatement("UPDATE class_sections SET length = ? WHERE id = ?");
            stmt.setInt(1, length);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            stmt.close();
        }
        
        // Save section-student relationships
        saveSectionStudents();
    }
    
    private void saveSectionStudents() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        // Clear existing relationships
        PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM section_students WHERE section_id = ?");
        deleteStmt.setInt(1, id);
        deleteStmt.executeUpdate();
        deleteStmt.close();
        
        // Insert new relationships
        PreparedStatement insertStmt = conn.prepareStatement(
            "INSERT INTO section_students (section_id, student_id) VALUES (?, ?)"
        );
        for (Student student : students) {
            insertStmt.setInt(1, id);
            insertStmt.setInt(2, student.getId());
            insertStmt.executeUpdate();
        }
        insertStmt.close();
    }
    
    public void loadStudents() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement("""
            SELECT s.* FROM students s 
            JOIN section_students ss ON s.id = ss.student_id 
            WHERE ss.section_id = ?
        """);
        stmt.setInt(1, id);
        ResultSet rs = stmt.executeQuery();
        
        students.clear();
        while (rs.next()) {
            students.add(new Student(
                rs.getInt("id"), 
                rs.getString("name"), 
                rs.getDouble("gpa")
            ));
        }
        
        stmt.close();
    }
    
    public static ClassSection findById(int id) throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM class_sections WHERE id = ?");
        stmt.setInt(1, id);
        ResultSet rs = stmt.executeQuery();
        
        ClassSection section = null;
        if (rs.next()) {
            section = new ClassSection(rs.getInt("id"), rs.getInt("length"));
            section.loadStudents();
        }
        
        stmt.close();
        return section;
    }
    
    public static List<ClassSection> findAll() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM class_sections");
        
        List<ClassSection> sections = new ArrayList<>();
        while (rs.next()) {
            ClassSection section = new ClassSection(rs.getInt("id"), rs.getInt("length"));
            section.loadStudents();
            sections.add(section);
        }
        
        stmt.close();
        return sections;
    }
    
    public void delete() throws SQLException {
        Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement("DELETE FROM class_sections WHERE id = ?");
        stmt.setInt(1, id);
        stmt.executeUpdate();
        stmt.close();
    }
    
    @Override
    public String toString() {
        return String.format("ClassSection{id=%d, length=%d, students=%d}", id, length, students.size());
    }
}

// Main School Manager class
public class SchoolManager {
    private Scanner scanner;
    
    public SchoolManager() {
        this.scanner = new Scanner(System.in);
    }
    
    public void run() {
        try {
            DatabaseConnection.initializeDatabase();
            System.out.println("School Manager Application Started");
            
            while (true) {
                showMainMenu();
                int choice = scanner.nextInt();
                scanner.nextLine(); // consume newline
                
                switch (choice) {
                    case 1 -> manageStudents();
                    case 2 -> manageTeachers();
                    case 3 -> manageSections();
                    case 4 -> viewReports();
                    case 5 -> {
                        System.out.println("Goodbye!");
                        return;
                    }
                    default -> System.out.println("Invalid choice. Please try again.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }
    
    private void showMainMenu() {
        System.out.println("\n=== School Manager ===");
        System.out.println("1. Manage Students");
        System.out.println("2. Manage Teachers");
        System.out.println("3. Manage Class Sections");
        System.out.println("4. View Reports");
        System.out.println("5. Exit");
        System.out.print("Enter your choice: ");
    }
    
    private void manageStudents() throws SQLException {
        System.out.println("\n=== Student Management ===");
        System.out.println("1. Add Student");
        System.out.println("2. View All Students");
        System.out.println("3. Update Student");
        System.out.println("4. Delete Student");
        System.out.print("Enter your choice: ");
        
        int choice = scanner.nextInt();
        scanner.nextLine();
        
        switch (choice) {
            case 1 -> addStudent();
            case 2 -> viewAllStudents();
            case 3 -> updateStudent();
            case 4 -> deleteStudent();
            default -> System.out.println("Invalid choice.");
        }
    }
    
    private void addStudent() throws SQLException {
        System.out.print("Enter student name: ");
        String name = scanner.nextLine();
        System.out.print("Enter student GPA: ");
        double gpa = scanner.nextDouble();
        
        Student student = new Student(name, gpa);
        student.save();
        System.out.println("Student added successfully with ID: " + student.getId());
    }
    
    private void viewAllStudents() throws SQLException {
        List<Student> students = Student.findAll();
        System.out.println("\n=== All Students ===");
        for (Student student : students) {
            System.out.println(student);
        }
    }
    
    private void updateStudent() throws SQLException {
        System.out.print("Enter student ID to update: ");
        int id = scanner.nextInt();
        scanner.nextLine();
        
        Student student = Student.findById(id);
        if (student != null) {
            System.out.print("Enter new name (current: " + student.getName() + "): ");
            String name = scanner.nextLine();
            System.out.print("Enter new GPA (current: " + student.getGpa() + "): ");
            double gpa = scanner.nextDouble();
            
            student.setName(name);
            student.setGpa(gpa);
            student.save();
            System.out.println("Student updated successfully.");
        } else {
            System.out.println("Student not found.");
        }
    }
    
    private void deleteStudent() throws SQLException {
        System.out.print("Enter student ID to delete: ");
        int id = scanner.nextInt();
        
        Student student = Student.findById(id);
        if (student != null) {
            student.delete();
            System.out.println("Student deleted successfully.");
        } else {
            System.out.println("Student not found.");
        }
    }
    
    private void manageTeachers() throws SQLException {
        System.out.println("\n=== Teacher Management ===");
        System.out.println("1. Add Teacher");
        System.out.println("2. View All Teachers");
        System.out.println("3. Assign Section to Teacher");
        System.out.print("Enter your choice: ");
        
        int choice = scanner.nextInt();
        scanner.nextLine();
        
        switch (choice) {
            case 1 -> addTeacher();
            case 2 -> viewAllTeachers();
            case 3 -> assignSectionToTeacher();
            default -> System.out.println("Invalid choice.");
        }
    }
    
    private void addTeacher() throws SQLException {
        System.out.print("Enter teacher name: ");
        String name = scanner.nextLine();
        
        Teacher teacher = new Teacher(name);
        teacher.save();
        System.out.println("Teacher added successfully with ID: " + teacher.getId());
    }
    
    private void viewAllTeachers() throws SQLException {
        List<Teacher> teachers = Teacher.findAll();
        System.out.println("\n=== All Teachers ===");
        for (Teacher teacher : teachers) {
            System.out.println(teacher);
        }
    }
    
    private void assignSectionToTeacher() throws SQLException {
        System.out.print("Enter teacher ID: ");
        int teacherId = scanner.nextInt();
        System.out.print("Enter section ID: ");
        int sectionId = scanner.nextInt();
        
        Teacher teacher = Teacher.findById(teacherId);
        ClassSection section = ClassSection.findById(sectionId);
        
        if (teacher != null && section != null) {
            teacher.addClassSection(section);
            teacher.save();
            System.out.println("Section assigned to teacher successfully.");
        } else {
            System.out.println("Teacher or section not found.");
        }
    }
    
    private void manageSections() throws SQLException {
        System.out.println("\n=== Section Management ===");
        System.out.println("1. Add Section");
        System.out.println("2. View All Sections");
        System.out.println("3. Add Student to Section");
        System.out.print("Enter your choice: ");
        
        int choice = scanner.nextInt();
        scanner.nextLine();
        
        switch (choice) {
            case 1 -> addSection();
            case 2 -> viewAllSections();
            case 3 -> addStudentToSection();
            default -> System.out.println("Invalid choice.");
        }
    }
    
    private void addSection() throws SQLException {
        System.out.print("Enter section length (in minutes): ");
        int length = scanner.nextInt();
        
        ClassSection section = new ClassSection(length);
        section.save();
        System.out.println("Section added successfully with ID: " + section.getId());
    }
    
    private void viewAllSections() throws SQLException {
        List<ClassSection> sections = ClassSection.findAll();
        System.out.println("\n=== All Sections ===");
        for (ClassSection section : sections) {
            System.out.println(section);
            System.out.println("  Students:");
            for (Student student : section.getStudents()) {
                System.out.println("    " + student);
            }
        }
    }
    
    private void addStudentToSection() throws SQLException {
        System.out.print("Enter section ID: ");
        int sectionId = scanner.nextInt();
        System.out.print("Enter student ID: ");
        int studentId = scanner.nextInt();
        
        ClassSection section = ClassSection.findById(sectionId);
        Student student = Student.findById(studentId);
        
        if (section != null && student != null) {
            section.addStudent(student);
            section.save();
            System.out.println("Student added to section successfully.");
        } else {
            System.out.println("Section or student not found.");
        }
    }
    
    private void viewReports() throws SQLException {
        System.out.println("\n=== Reports ===");
        System.out.println("Total Students: " + Student.findAll().size());
        System.out.println("Total Teachers: " + Teacher.findAll().size());
        System.out.println("Total Sections: " + ClassSection.findAll().size());
        
        // Average GPA
        List<Student> students = Student.findAll();
        double totalGPA = students.stream().mapToDouble(Student::getGpa).sum();
        double avgGPA = students.isEmpty() ? 0 : totalGPA / students.size();
        System.out.printf("Average GPA: %.2f\n", avgGPA);
    }
    
    public static void main(String[] args) {
        SchoolManager manager = new SchoolManager();
        manager.run();
    }
}