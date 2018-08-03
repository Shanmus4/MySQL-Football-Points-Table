-- phpMyAdmin SQL Dump
-- version 4.7.4
-- https://www.phpmyadmin.net/
--
-- Host: localhost
-- Generation Time: Oct 09, 2017 at 04:01 PM
-- Server version: 5.7.19-log
-- PHP Version: 7.1.8

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET AUTOCOMMIT = 0;
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `football`
--

-- --------------------------------------------------------

--
-- Table structure for table `ballondor`
--

CREATE TABLE `ballondor` (
  `Player` varchar(15) DEFAULT NULL,
  `Goals` int(2) DEFAULT NULL,
  `Assists` int(2) DEFAULT NULL,
  `Trophies` varchar(50) DEFAULT NULL,
  `Votes` int(5) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `ballondor`
--

INSERT INTO `ballondor` (`Player`, `Goals`, `Assists`, `Trophies`, `Votes`) VALUES
('Dybala', 26, 6, 'South America Cup,Argentina Champion', 36000),
('Suarez', 33, 7, 'UEFA Champions Trophy', 50000),
('Ronaldo', 36, 10, 'La Liga trophy, Euro Champion, World Cup', 56000),
('Messi', 37, 9, 'Golden Boot, Golden Ball, Star,\nChampions Trophy', 96000);

-- --------------------------------------------------------

--
-- Table structure for table `club_stats`
--

CREATE TABLE `club_stats` (
  `club` varchar(15) NOT NULL,
  `Matchesplayed` int(2) DEFAULT NULL,
  `Wins` int(2) DEFAULT NULL,
  `Draws` int(2) DEFAULT NULL,
  `Loss` int(2) DEFAULT NULL,
  `GoalsFor` int(3) DEFAULT NULL,
  `GoalsAgainst` int(3) DEFAULT NULL,
  `GoalDiff` int(3) DEFAULT NULL,
  `Points` int(2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `club_stats`
--

INSERT INTO `club_stats` (`club`, `Matchesplayed`, `Wins`, `Draws`, `Loss`, `GoalsFor`, `GoalsAgainst`, `GoalDiff`, `Points`) VALUES
('Atletico', 38, 23, 9, 6, 70, 27, 43, 78),
('Barcelona', 38, 28, 6, 4, 116, 37, 79, 90),
('Real Madrid', 38, 29, 6, 3, 107, 41, 65, 93),
('Sevilla', 38, 21, 9, 8, 69, 49, 20, 72),
('Villareal', 38, 19, 10, 9, 56, 33, 23, 67);

-- --------------------------------------------------------

--
-- Table structure for table `duall`
--

CREATE TABLE `duall` (
  `RESULT` varchar(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- --------------------------------------------------------

--
-- Table structure for table `incentive`
--

CREATE TABLE `incentive` (
  `EmprefID` int(11) DEFAULT NULL,
  `iDate` date DEFAULT NULL,
  `iAmt` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `incentive`
--

INSERT INTO `incentive` (`EmprefID`, `iDate`, `iAmt`) VALUES
(1, '2013-02-01', 5000),
(2, '2013-02-01', 3000),
(3, '2013-02-01', 4000),
(4, '2013-01-01', 4500),
(2, '2013-01-01', 3500);

-- --------------------------------------------------------

--
-- Table structure for table `manager_stats`
--

CREATE TABLE `manager_stats` (
  `Manager` varchar(15) NOT NULL,
  `Club` varchar(15) DEFAULT NULL,
  `Wins` int(3) DEFAULT NULL,
  `Draws` int(3) DEFAULT NULL,
  `Loss` int(3) DEFAULT NULL,
  `Nation` varchar(15) DEFAULT NULL,
  `Trophies` int(2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `manager_stats`
--

INSERT INTO `manager_stats` (`Manager`, `Club`, `Wins`, `Draws`, `Loss`, `Nation`, `Trophies`) VALUES
('Carlo Ancelotti', 'Bayern Munich', 110, 50, 38, 'Italy', 6),
('Jose Mourinho', 'MachesterUnited', 110, 60, 28, 'Portugal', 15),
('Luis Enrique', 'Barcelona', 135, 56, 25, 'Spain', 15),
('Pep Guardiola', 'Manchester City', 127, 58, 23, 'Spain', 17);

-- --------------------------------------------------------

--
-- Table structure for table `player_stats`
--

CREATE TABLE `player_stats` (
  `Player` varchar(15) NOT NULL,
  `Goals` int(3) DEFAULT NULL,
  `Assists` int(3) DEFAULT NULL,
  `Red` int(2) DEFAULT NULL,
  `Yellow` int(2) DEFAULT NULL,
  `Saves` int(2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `player_stats`
--

INSERT INTO `player_stats` (`Player`, `Goals`, `Assists`, `Red`, `Yellow`, `Saves`) VALUES
('Messi', 37, 9, 0, 6, 0),
('Neymar', 13, 11, 1, 6, 0),
('Pique', 2, 0, 0, 0, 0),
('Suarez', 29, 13, 0, 8, 0),
('Ter Stegen', 0, 0, 0, 1, 90);

-- --------------------------------------------------------

--
-- Table structure for table `pma__tracking`
--

CREATE TABLE `pma__tracking` (
  `db_name` varchar(64) COLLATE utf8_bin NOT NULL,
  `table_name` varchar(64) COLLATE utf8_bin NOT NULL,
  `version` int(10) UNSIGNED NOT NULL,
  `date_created` datetime NOT NULL,
  `date_updated` datetime NOT NULL,
  `schema_snapshot` text COLLATE utf8_bin NOT NULL,
  `schema_sql` text COLLATE utf8_bin,
  `data_sql` longtext COLLATE utf8_bin,
  `tracking` set('UPDATE','REPLACE','INSERT','DELETE','TRUNCATE','CREATE DATABASE','ALTER DATABASE','DROP DATABASE','CREATE TABLE','ALTER TABLE','RENAME TABLE','DROP TABLE','CREATE INDEX','DROP INDEX','CREATE VIEW','ALTER VIEW','DROP VIEW') COLLATE utf8_bin DEFAULT NULL,
  `tracking_active` int(1) UNSIGNED NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='Database changes tracking for phpMyAdmin';

-- --------------------------------------------------------

--
-- Table structure for table `top_player`
--

CREATE TABLE `top_player` (
  `Player` varchar(15) NOT NULL,
  `Goals` int(2) DEFAULT NULL,
  `Assists` int(2) DEFAULT NULL,
  `Pass` int(2) DEFAULT NULL,
  `Achievements` varchar(50) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Dumping data for table `top_player`
--

INSERT INTO `top_player` (`Player`, `Goals`, `Assists`, `Pass`, `Achievements`) VALUES
('Kroos', 5, 6, 58, 'NIL'),
('Messi', 37, 9, 99, 'Longest Dribble, Golden Boot,Golden Ball, Puskas'),
('Neymar', 13, 11, 73, 'Best Trick, Most Style, UEFA Player of the month'),
('Ronaldo', 36, 12, 96, 'Best Player,Golden Boot,Golden Ball,Longest Goal');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `ballondor`
--
ALTER TABLE `ballondor`
  ADD PRIMARY KEY (`Votes`);

--
-- Indexes for table `club_stats`
--
ALTER TABLE `club_stats`
  ADD PRIMARY KEY (`club`);

--
-- Indexes for table `manager_stats`
--
ALTER TABLE `manager_stats`
  ADD PRIMARY KEY (`Manager`);

--
-- Indexes for table `player_stats`
--
ALTER TABLE `player_stats`
  ADD PRIMARY KEY (`Player`);

--
-- Indexes for table `pma__tracking`
--
ALTER TABLE `pma__tracking`
  ADD PRIMARY KEY (`db_name`,`table_name`,`version`);

--
-- Indexes for table `top_player`
--
ALTER TABLE `top_player`
  ADD PRIMARY KEY (`Player`),
  ADD UNIQUE KEY `Player` (`Player`,`Achievements`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
